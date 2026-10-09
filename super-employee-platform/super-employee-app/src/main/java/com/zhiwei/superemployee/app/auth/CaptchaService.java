package com.zhiwei.superemployee.app.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录验证码服务。
 * <p>
 * 在内存中维护验证码挑战与客户端发放限流窗口，支持字母数字、算术与滑块三类题型；
 * 提供 Base64 PNG 图片下发与一次性校验能力，供登录接口防暴力破解使用。
 * </p>
 * @author yangdb
 */
@Service
public class CaptchaService {
    /** 字母数字题型可用字符集（排除易混淆字符）。 */
    private static final String CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private final SecureRandom random = new SecureRandom();
    /** 未校验或未过期的挑战缓存，键为验证码 ID。 */
    private final Map<String, Challenge> challenges = new ConcurrentHashMap<>();
    /** 按客户端维度统计每分钟发放次数。 */
    private final Map<String, IssueWindow> issueWindows = new ConcurrentHashMap<>();
    /** 验证码有效期（秒）。 */
    private final long ttlSeconds;
    /** 单次挑战最大错误尝试次数。 */
    private final int maxAttempts;
    /** 配置的验证码类型（RANDOM / ALPHANUMERIC / ARITHMETIC / SLIDER）。 */
    private final String configuredType;
    /** 同时存在的最大挑战数量。 */
    private final int maxChallenges;
    /** 同一客户端每分钟最大发放次数。 */
    private final int maxIssuesPerMinute;

    /**
     * 通过配置项构造验证码服务，并对边界值做下限/下限保护。
     *
     * @param ttlSeconds         有效期秒数，实际至少 30
     * @param maxAttempts        最大尝试次数，实际至少 1
     * @param configuredType     题型配置，RANDOM 表示随机选取
     * @param maxChallenges      最大活跃挑战数，实际至少 100
     * @param maxIssuesPerMinute 每分钟最大发放数，实际至少 5
     */
    public CaptchaService(
            @Value("${super-employee.security.captcha.ttl-seconds:120}") long ttlSeconds,
            @Value("${super-employee.security.captcha.max-attempts:5}") int maxAttempts,
            @Value("${super-employee.security.captcha.type:RANDOM}") String configuredType,
            @Value("${super-employee.security.captcha.max-active:10000}") int maxChallenges,
            @Value("${super-employee.security.captcha.max-issues-per-minute:30}") int maxIssuesPerMinute) {
        this.ttlSeconds = Math.max(30, ttlSeconds);
        this.maxAttempts = Math.max(1, maxAttempts);
        this.configuredType = configuredType;
        this.maxChallenges = Math.max(100, maxChallenges);
        this.maxIssuesPerMinute = Math.max(5, maxIssuesPerMinute);
    }

    /**
     * 为指定客户端创建一次验证码挑战，并返回前端渲染所需字段。
     * <p>返回 Map 通常包含 {@code id}、{@code type}、{@code expiresIn}、{@code image}（data URL）。</p>
     *
     * @param clientKey 客户端标识（如 IP 或会话键），用于发放限流与校验绑定
     * @return 含验证码 ID、类型、过期秒数与图片 data URL 的有序结果
     * @throws IllegalStateException 当发放过频、活跃挑战过多或图片生成失败时
     */
    public Map<String, Object> create(String clientKey) {
        cleanup();
        checkIssueRate(clientKey);
        if (challenges.size() >= maxChallenges) {
            throw new IllegalStateException("验证码服务繁忙，请稍后重试");
        }
        String type = normalizeType(configuredType);
        String id = UUID.randomUUID().toString();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("type", type);
        result.put("expiresIn", ttlSeconds);

        String answer;
        if ("SLIDER".equals(type)) {
            int target = 24 + random.nextInt(65);
            answer = String.valueOf(target);
            result.put("image", renderSliderImage(target));
        }
        else {
            String text;
            if ("ARITHMETIC".equals(type)) {
                int left = 1 + random.nextInt(20);
                int right = 1 + random.nextInt(20);
                boolean add = random.nextBoolean();
                if (!add && right > left) {
                    int swap = left;
                    left = right;
                    right = swap;
                }
                answer = String.valueOf(add ? left + right : left - right);
                text = left + (add ? " + " : " − ") + right + " = ?";
            }
            else {
                StringBuilder code = new StringBuilder(5);
                for (int i = 0; i < 5; i++) {
                    code.append(CHARS.charAt(random.nextInt(CHARS.length())));
                }
                answer = code.toString();
                text = answer;
            }
            result.put("image", renderImage(text));
        }
        challenges.put(id, new Challenge(answer.toUpperCase(Locale.ROOT),
                Instant.now().plusSeconds(ttlSeconds), 0, type, clientKey));
        return result;
    }

    /**
     * 校验用户提交的验证码答案。
     * <p>
     * 校验失败会累加尝试次数；成功或过期/超限/客户端不匹配时会移除挑战。
     * 滑块题型允许与目标值相差不超过 3 的误差。
     * </p>
     *
     * @param id        验证码挑战 ID
     * @param value     用户提交的答案（字母数字会忽略大小写）
     * @param clientKey 必须与创建时绑定的客户端标识一致
     * @return 校验通过返回 true，否则 false
     */
    public boolean verify(String id, String value, String clientKey) {
        if (id == null || value == null) return false;
        Challenge current = challenges.get(id);
        if (current == null || Instant.now().isAfter(current.expiresAt())
                || current.attempts() >= maxAttempts
                || !current.clientKey().equals(clientKey)) {
            challenges.remove(id);
            return false;
        }
        boolean valid;
        if ("SLIDER".equals(current.type())) {
            try {
                valid = Math.abs(Integer.parseInt(current.answer()) - Integer.parseInt(value.trim())) <= 3;
            }
            catch (NumberFormatException ex) {
                valid = false;
            }
        }
        else {
            valid = current.answer().equals(value.trim().toUpperCase(Locale.ROOT));
        }
        if (valid) {
            challenges.remove(id);
            return true;
        }
        challenges.put(id, new Challenge(current.answer(), current.expiresAt(),
                current.attempts() + 1, current.type(), current.clientKey()));
        return false;
    }

    /**
     * 检查客户端验证码发放频率，超出每分钟上限则拒绝。
     *
     * @param clientKey 客户端标识
     * @throws IllegalStateException 发放过于频繁时
     */
    private void checkIssueRate(String clientKey) {
        Instant now = Instant.now();
        IssueWindow updated = issueWindows.compute(clientKey, (ignored, current) -> {
            if (current == null || now.isAfter(current.startedAt().plusSeconds(60))) {
                return new IssueWindow(1, now);
            }
            return new IssueWindow(current.count() + 1, current.startedAt());
        });
        if (updated.count() > maxIssuesPerMinute) {
            throw new IllegalStateException("验证码刷新过于频繁，请稍后再试");
        }
    }

    /**
     * 规范化配置题型；RANDOM 时随机选择三种具体类型之一。
     *
     * @param requestedType 配置或请求的类型字符串
     * @return 规范化后的类型：ALPHANUMERIC / ARITHMETIC / SLIDER
     */
    private String normalizeType(String requestedType) {
        String type = requestedType == null ? "RANDOM" : requestedType.trim().toUpperCase(Locale.ROOT);
        if ("RANDOM".equals(type)) {
            return switch (random.nextInt(3)) {
                case 0 -> "ALPHANUMERIC";
                case 1 -> "ARITHMETIC";
                default -> "SLIDER";
            };
        }
        return switch (type) {
            case "ALPHANUMERIC", "ARITHMETIC", "SLIDER" -> type;
            default -> "ALPHANUMERIC";
        };
    }

    /**
     * 将字母数字或算术题干渲染为 PNG，并返回 data URL。
     *
     * @param text 题干文本
     * @return {@code data:image/png;base64,...} 形式字符串
     * @throws IllegalStateException 图片编码失败时
     */
    private String renderImage(String text) {
        try {
            BufferedImage image = new BufferedImage(168, 48, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(238, 246, 250));
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            for (int i = 0; i < 8; i++) {
                graphics.setColor(new Color(random.nextInt(150), random.nextInt(170), random.nextInt(190), 90));
                graphics.setStroke(new BasicStroke(1f));
                graphics.drawLine(random.nextInt(168), random.nextInt(48),
                        random.nextInt(168), random.nextInt(48));
            }
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, text.length() > 6 ? 22 : 28));
            graphics.setColor(new Color(16, 42, 67));
            graphics.drawString(text, 12, 34);
            graphics.dispose();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
        }
        catch (Exception ex) {
            throw new IllegalStateException("生成验证码失败", ex);
        }
    }

    /**
     * 渲染滑块验证码背景图，缺口位置对应目标百分比。
     *
     * @param targetPercent 目标滑块位置百分比（约 24–88）
     * @return PNG data URL
     * @throws IllegalStateException 图片编码失败时
     */
    private String renderSliderImage(int targetPercent) {
        try {
            int width = 360;
            int height = 72;
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setPaint(new java.awt.GradientPaint(0, 0, new Color(220, 240, 245),
                    width, height, new Color(244, 248, 251)));
            graphics.fillRect(0, 0, width, height);
            for (int i = 0; i < 18; i++) {
                graphics.setColor(new Color(60 + random.nextInt(120), 100 + random.nextInt(100),
                        120 + random.nextInt(100), 70));
                graphics.fillOval(random.nextInt(width), random.nextInt(height),
                        8 + random.nextInt(28), 8 + random.nextInt(28));
            }
            int slotX = Math.round(width * targetPercent / 100f) - 15;
            graphics.setColor(new Color(255, 255, 255, 210));
            graphics.fillRoundRect(slotX, 20, 30, 30, 6, 6);
            graphics.setColor(new Color(20, 125, 146));
            graphics.setStroke(new BasicStroke(2f));
            graphics.drawRoundRect(slotX, 20, 30, 30, 6, 6);
            graphics.dispose();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
        }
        catch (Exception ex) {
            throw new IllegalStateException("生成滑块验证码失败", ex);
        }
    }

    /** 清理已过期的挑战与超过一分钟的发放窗口。 */
    private void cleanup() {
        Instant now = Instant.now();
        challenges.entrySet().removeIf(entry -> now.isAfter(entry.getValue().expiresAt()));
        issueWindows.entrySet().removeIf(entry -> now.isAfter(entry.getValue().startedAt().plusSeconds(60)));
    }

    /**
     * 内存中的单次验证码挑战。
     *
     * @param answer    标准答案（字母数字已大写）
     * @param expiresAt 过期时间
     * @param attempts  已失败尝试次数
     * @param type      题型
     * @param clientKey 绑定客户端
     */
    private record Challenge(String answer, Instant expiresAt, int attempts, String type, String clientKey) {}

    /**
     * 客户端发放限流窗口。
     *
     * @param count     窗口内已发放次数
     * @param startedAt 窗口起始时间
     */
    private record IssueWindow(int count, Instant startedAt) {}
}
