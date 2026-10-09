<!-- @author yangdb -->
<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Document, Edit, MagicStick, Plus, Refresh, Search, Upload } from '@element-plus/icons-vue'
import { createKnowledge, deleteKnowledge, generateKnowledgeDraft, listKnowledge, updateKnowledge } from '../api'
import { useTablePagination } from '../useTablePagination'

const items = ref([])
const loading = ref(false)
const visible = ref(false)
const editingId = ref('')
const keyword = ref('')
const formRef = ref()
const generating = ref(false)
const deletingId = ref('')
const uploadFile = ref(null)
const generateHint = ref('')
const generateMeta = ref(null)
const form = reactive({ title: '', category: 'RUNBOOK', tags: '', scope: 'OPS', content: '', enabled: true })
const rules = {
  title: [{ required: true, message: '请输入知识标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入知识内容', trigger: 'blur' }]
}
const filtered = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  return items.value.filter(x => !q || `${x.title} ${x.tags} ${x.content}`.toLowerCase().includes(q))
})
const { page, pageSize, total, paged, onSizeChange } = useTablePagination(filtered)
const stats = computed(() => ({
  total: items.value.length,
  enabled: items.value.filter(x => x.enabled).length,
  incident: items.value.filter(x => x.category === 'INCIDENT').length,
  hits: items.value.reduce((n, x) => n + (x.hitCount || 0), 0)
}))
const categoryLabels = { RUNBOOK: '运行手册', INCIDENT: '故障经验', CONFIG: '配置规范' }
const categoryTypes = { RUNBOOK: 'primary', INCIDENT: 'warning', CONFIG: 'success' }
function categoryLabel(value) { return categoryLabels[value] || value || '未分类' }
function tagList(value) { return String(value || '').split(/[,，;；]/).map(x => x.trim()).filter(Boolean).slice(0, 6) }
function contentSummary(value) { return String(value || '').replace(/\s+/g, ' ').trim() || '暂无知识内容' }

async function load() {
  loading.value = true
  try { items.value = await listKnowledge() }
  catch (e) { ElMessage.error(e.message) }
  finally { loading.value = false }
}

function resetGenerateAids() {
  uploadFile.value = null
  generateHint.value = ''
  generateMeta.value = null
}

function openCreate(focusImport = false) {
  editingId.value = ''
  Object.assign(form, { title: '', category: 'RUNBOOK', tags: '', scope: 'OPS', content: '', enabled: true })
  resetGenerateAids()
  visible.value = true
  if (focusImport) {
    generateHint.value = '请上传文档或填写描述后点击「AI 预生成」。'
  }
}

function openEdit(x) {
  editingId.value = x.id
  Object.assign(form, {
    title: x.title,
    category: x.category,
    tags: x.tags || '',
    scope: x.scope || 'OPS',
    content: x.content,
    enabled: x.enabled
  })
  resetGenerateAids()
  visible.value = true
}

function onFileChange(uploadFileInfo) {
  uploadFile.value = uploadFileInfo?.raw || null
}

function onFileRemove() {
  uploadFile.value = null
}

async function runGenerate() {
  if (!uploadFile.value && !generateHint.value.trim() && !form.content.trim() && !form.title.trim()) {
    ElMessage.warning('请先上传文档，或在下方填写描述/标题后再预生成')
    return
  }
  generating.value = true
  try {
    const description = [generateHint.value.trim(), form.title ? `标题线索：${form.title}` : '', form.content.trim() ? `已有正文补充：\n${form.content}` : '']
      .filter(Boolean)
      .join('\n\n')
    const draft = await generateKnowledgeDraft({
      file: uploadFile.value,
      description,
      category: form.category,
      scope: form.scope
    })
    Object.assign(form, {
      title: draft.title || form.title,
      category: draft.category || form.category,
      tags: draft.tags || form.tags,
      scope: draft.scope || form.scope,
      content: draft.content || form.content,
      enabled: draft.enabled !== false
    })
    generateMeta.value = {
      sourceFileName: draft.sourceFileName,
      truncated: draft.truncated,
      generationMode: draft.generationMode
    }
    formRef.value?.clearValidate()
    ElMessage.success(draft.generationMode === 'AI'
      ? '已根据材料生成草稿，请核对后保存'
      : '模型暂不可用，已按文档本地整理草稿，请核对后保存')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    generating.value = false
  }
}

async function save() {
  await formRef.value.validate()
  try {
    if (editingId.value) await updateKnowledge(editingId.value, form)
    else await createKnowledge(form)
    visible.value = false
    await load()
    ElMessage.success('知识条目已保存')
  } catch (e) { ElMessage.error(e.message) }
}

async function remove(x) {
  try {
    await ElMessageBox.confirm(`确认删除“${x.title}”？`, '删除知识', { type: 'warning' })
    deletingId.value = x.id
    await deleteKnowledge(x.id)
    items.value = items.value.filter(item => item.id !== x.id)
    ElMessage.success('已删除')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error?.message || '删除失败，请稍后重试')
    }
  } finally {
    deletingId.value = ''
  }
}

onMounted(load)
</script>

<template>
  <section class="management-page page-fixed-layout knowledge-page">
    <div class="page-fixed-overview">
      <div class="page-heading">
        <div>
          <span class="page-kicker">OPERATIONS KNOWLEDGE</span>
          <h2>知识库</h2>
          <p>沉淀运行手册、故障经验和修复方案；支持文档导入与 AI 预生成，运维 Agent 会自动召回相关知识。</p>
        </div>
        <div class="heading-actions">
          <el-button :icon="Upload" @click="openCreate(true)">导入/AI 生成</el-button>
          <el-button type="primary" :icon="Plus" @click="openCreate(false)">新增知识</el-button>
        </div>
      </div>
      <div class="agent-metrics">
        <div><span>知识总数</span><strong>{{ stats.total }}</strong></div>
        <div><span>已启用</span><strong class="metric-success">{{ stats.enabled }}</strong></div>
        <div><span>事件经验</span><strong>{{ stats.incident }}</strong></div>
        <div><span>累计召回</span><strong class="metric-primary">{{ stats.hits }}</strong></div>
      </div>
    </div>
    <div class="management-section page-fixed-body knowledge-body">
      <div class="knowledge-toolbar">
        <el-input v-model="keyword" clearable :prefix-icon="Search" placeholder="搜索标题、标签或知识正文" />
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>
      <div class="table-heading knowledge-list-heading">
        <div><h3>知识条目</h3><span>共 {{ total }} 条记录，供运维 Agent 检索与召回</span></div>
      </div>
      <div v-loading="loading" class="knowledge-card-scroll">
        <div v-if="paged.length" class="knowledge-card-grid">
          <article v-for="row in paged" :key="row.id" class="knowledge-card">
            <header>
              <div class="knowledge-card-title">
                <el-tag size="small" effect="light" :type="categoryTypes[row.category] || 'info'">{{ categoryLabel(row.category) }}</el-tag>
                <el-tag size="small" effect="plain" type="info">{{ row.scope === 'GLOBAL' ? '全局' : '运维' }}</el-tag>
                <el-tag size="small" effect="plain" :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '已启用' : '已停用' }}</el-tag>
              </div>
              <div class="knowledge-card-actions">
                <el-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
                <el-button
                  link
                  type="danger"
                  :icon="Delete"
                  :loading="deletingId === row.id"
                  :disabled="!!deletingId && deletingId !== row.id"
                  @click="remove(row)"
                >删除</el-button>
              </div>
            </header>
            <h4>{{ row.title }}</h4>
            <p class="knowledge-summary">{{ contentSummary(row.content) }}</p>
            <div class="knowledge-tags">
              <span v-for="tag in tagList(row.tags)" :key="tag"># {{ tag }}</span>
              <span v-if="!tagList(row.tags).length" class="no-tag">暂无标签</span>
            </div>
            <footer>
              <span>{{ row.sourceIncidentId ? '故障自动沉淀' : '人工维护' }}</span>
              <span class="knowledge-hit">已召回 <strong>{{ row.hitCount || 0 }}</strong> 次</span>
            </footer>
          </article>
        </div>
        <el-empty v-else description="暂无匹配的知识条目" :image-size="84" />
      </div>
      <div class="table-pagination">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          background
          layout="total, sizes, prev, pager, next"
          :page-sizes="[20, 50, 100]"
          :total="total"
          @size-change="onSizeChange"
        />
      </div>
    </div>

    <el-dialog v-model="visible" :title="editingId ? '编辑知识' : '新增知识'" width="min(820px, 94vw)" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="editor-form">
        <div v-if="!editingId" class="knowledge-generate-panel">
          <div class="knowledge-generate-title">
            <strong>导入文档 / AI 预生成</strong>
            <span>先生成草稿再人工确认保存；支持 txt、md、docx、log、json、yaml 等</span>
          </div>
          <el-upload
            drag
            :auto-upload="false"
            :limit="1"
            accept=".txt,.md,.markdown,.log,.csv,.json,.yml,.yaml,.xml,.properties,.html,.htm,.docx,.conf,.ini"
            :on-change="onFileChange"
            :on-remove="onFileRemove"
          >
            <el-icon class="el-icon--upload"><Upload /></el-icon>
            <div class="el-upload__text">拖拽文档到此处，或 <em>点击选择</em></div>
          </el-upload>
          <el-input
            v-model="generateHint"
            type="textarea"
            :rows="3"
            placeholder="可选：补充现象、系统名称、期望沉淀重点等，将与文档一并交给 Agent 整理"
          />
          <div class="knowledge-generate-actions">
            <el-button type="primary" :icon="MagicStick" :loading="generating" @click="runGenerate">AI 预生成</el-button>
            <span v-if="generateMeta" class="form-item-desc" style="margin: 0;">
              <el-icon><Document /></el-icon>
              {{ generateMeta.generationMode === 'AI' ? 'AI 生成' : '本地整理' }}
              <template v-if="generateMeta.sourceFileName"> · {{ generateMeta.sourceFileName }}</template>
              <template v-if="generateMeta.truncated"> · 原文已截断</template>
            </span>
          </div>
          <p class="form-item-desc">预生成不会直接入库；请核对下方标题与正文后再点保存。</p>
        </div>

        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" />
          <p class="form-item-desc">便于检索的短标题，建议包含系统或故障关键词。</p>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="类型">
              <el-select v-model="form.category" class="full-width">
                <el-option label="运行手册" value="RUNBOOK" />
                <el-option label="故障经验" value="INCIDENT" />
                <el-option label="配置规范" value="CONFIG" />
              </el-select>
              <p class="form-item-desc">知识分类，影响筛选与推荐。</p>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="适用范围">
              <el-select v-model="form.scope" class="full-width">
                <el-option label="运维" value="OPS" />
                <el-option label="全局" value="GLOBAL" />
              </el-select>
              <p class="form-item-desc">限定可见范围；全局可被更多场景引用。</p>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="状态">
              <el-switch v-model="form.enabled" active-text="启用" />
              <p class="form-item-desc">停用后不再参与检索与推荐。</p>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="标签">
          <el-input v-model="form.tags" placeholder="如：Redis,验证码,广州人才" />
          <p class="form-item-desc">逗号分隔关键词，便于模糊匹配。</p>
        </el-form-item>
        <el-form-item label="知识内容" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="12" placeholder="现象、判断依据、处置步骤、验证方式和回滚方案" />
          <p class="form-item-desc">建议按现象 → 判断 → 处置 → 验证 → 回滚结构书写，便于 Agent 引用。</p>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.knowledge-body {
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: 18px 20px 14px;
}
.knowledge-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-bottom: 16px;
  border-bottom: 1px solid #e6edf5;
}
.knowledge-toolbar .el-input { max-width: 620px; }
.knowledge-list-heading { margin: 18px 0 14px; }
.knowledge-card-scroll {
  flex: 1 1 auto;
  min-height: 0;
  overflow: auto;
}
.knowledge-card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
  gap: 14px;
  padding: 1px 2px 12px;
}
.knowledge-card {
  display: flex;
  min-height: 220px;
  flex-direction: column;
  padding: 17px 18px 14px;
  border: 1px solid #dfe7f0;
  border-radius: 10px;
  background: linear-gradient(180deg, #fff 0%, #fbfdff 100%);
  box-shadow: 0 2px 8px rgba(16, 42, 67, .035);
  transition: border-color .2s, box-shadow .2s, transform .2s;
}
.knowledge-card:hover {
  border-color: #91bce8;
  box-shadow: 0 8px 22px rgba(16, 42, 67, .09);
  transform: translateY(-1px);
}
.knowledge-card header,
.knowledge-card footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.knowledge-card-title,
.knowledge-card-actions,
.knowledge-tags {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 7px;
}
.knowledge-card h4 {
  margin: 16px 0 8px;
  color: #102a43;
  font-size: 16px;
  line-height: 1.45;
}
.knowledge-summary {
  display: -webkit-box;
  min-height: 66px;
  margin: 0 0 13px;
  overflow: hidden;
  color: #486581;
  font-size: 13px;
  line-height: 1.7;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}
.knowledge-tags { min-height: 26px; margin-bottom: 14px; }
.knowledge-tags span {
  padding: 3px 8px;
  border-radius: 12px;
  background: #edf6ff;
  color: #2774a8;
  font-size: 12px;
}
.knowledge-tags .no-tag { background: #f1f3f5; color: #9aa5b1; }
.knowledge-card footer {
  margin-top: auto;
  padding-top: 12px;
  border-top: 1px solid #e9eff5;
  color: #829ab1;
  font-size: 12px;
}
.knowledge-hit strong { color: #147d92; font-size: 14px; }
.knowledge-body .table-pagination {
  flex: none;
  margin-top: 8px;
  padding-top: 12px;
  border-top: 1px solid #e6edf5;
}
.knowledge-generate-panel {
  margin-bottom: 18px;
  padding: 14px;
  border: 1px solid #cfe3ee;
  border-radius: 8px;
  background: linear-gradient(135deg, #f5fbff 0%, #f8fafc 100%);
}
.knowledge-generate-title {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 12px;
}
.knowledge-generate-title strong { color: #243b53; font-size: 14px; }
.knowledge-generate-title span { color: #829ab1; font-size: 12px; }
.knowledge-generate-panel :deep(.el-upload) { width: 100%; }
.knowledge-generate-panel :deep(.el-upload-dragger) {
  width: 100%;
  padding: 18px 12px;
}
.knowledge-generate-panel .el-textarea { margin-top: 10px; }
.knowledge-generate-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 12px;
}
.knowledge-generate-actions .form-item-desc {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
@media (max-width: 760px) {
  .knowledge-card-grid { grid-template-columns: 1fr; }
  .knowledge-toolbar { align-items: stretch; }
  .knowledge-toolbar .el-input { max-width: none; }
}
</style>
