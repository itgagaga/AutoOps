<script setup>
import { computed } from 'vue'
import { CircleCheck, CircleClose } from '@element-plus/icons-vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  title: { type: String, default: '' },
  repairFiles: { type: Array, default: () => [] },
  repairDiffFiles: { type: Array, default: () => [] },
  repairPending: { type: Boolean, default: false },
  repairReviewing: { type: Boolean, default: false },
  reviewStatus: { type: String, default: '' }
})

const emit = defineEmits(['update:visible', 'review'])

function close() {
  emit('update:visible', false)
}

function handleReview(accepted) {
  emit('review', accepted)
}

const statusLabel = computed(() => {
  if (props.reviewStatus === 'ACCEPTED') return '已接受'
  if (props.reviewStatus === 'REVERTED') return '已撤销'
  return '待确认'
})

const statusType = computed(() => {
  if (props.reviewStatus === 'ACCEPTED') return 'success'
  if (props.reviewStatus === 'REVERTED') return 'info'
  return 'warning'
})
</script>

<template>
  <el-dialog
    :model-value="visible"
    :title="title"
    width="min(1500px, 94vw)"
    top="4vh"
    append-to-body
    class="repair-diff-dialog"
    @update:model-value="(v) => emit('update:visible', v)"
  >
    <div class="diff-dialog-meta">
      <span>{{ repairFiles.length }} 个修改文件</span>
      <el-tag size="small" :type="statusType">{{ statusLabel }}</el-tag>
    </div>
    <div class="side-by-side-diff">
      <section v-for="file in repairDiffFiles" :key="file.path" class="diff-file">
        <header class="diff-file-header">
          <span>{{ file.path }}</span>
          <span class="diff-file-stat">
            <b class="diff-add-count">+{{ file.additions }}</b>
            <b class="diff-remove-count">-{{ file.deletions }}</b>
          </span>
        </header>
        <div class="diff-file-grid">
          <div class="diff-column-title">
            修复前
            <span v-if="file.beforeMissing" class="diff-missing-tip">历史快照未记录</span>
          </div>
          <div class="diff-column-title">
            修复后
            <span v-if="file.afterMissing" class="diff-missing-tip">文件已删除</span>
          </div>
          <template v-for="(row, index) in file.rows" :key="index">
            <div v-if="row.type === 'skip'" class="diff-skip">
              {{ row.count }} 行未修改
            </div>
            <template v-else>
              <div :class="['diff-cell', 'diff-left', row.type === 'change' ? 'diff-remove' : `diff-${row.type}`, { 'diff-empty': row.leftNo == null }]">
                <span class="diff-line-number">{{ row.leftNo ?? '' }}</span>
                <pre>{{ row.left || ' ' }}</pre>
              </div>
              <div :class="['diff-cell', row.type === 'change' ? 'diff-add' : `diff-${row.type}`, { 'diff-empty': row.rightNo == null }]">
                <span class="diff-line-number">{{ row.rightNo ?? '' }}</span>
                <pre>{{ row.right || ' ' }}</pre>
              </div>
            </template>
          </template>
        </div>
      </section>
      <el-empty v-if="!repairDiffFiles.length" :image-size="60" description="暂无可展示的代码差异" />
    </div>
    <template #footer>
      <el-button @click="close">关闭</el-button>
      <template v-if="repairPending">
        <el-button type="warning" :disabled="repairReviewing" @click="handleReview(false)">撤销修复</el-button>
        <el-button type="success" :loading="repairReviewing" @click="handleReview(true)">接受并继续验证</el-button>
      </template>
    </template>
  </el-dialog>
</template>
