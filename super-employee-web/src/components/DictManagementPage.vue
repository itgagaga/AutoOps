<!-- @author yangdb -->
<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Edit, Plus, Refresh, Search } from '@element-plus/icons-vue'
import {
  createDictItem, createDictType, deleteDictItem, deleteDictType,
  listDictItems, listDictTypes, setDictItemEnabled, updateDictItem, updateDictType
} from '../api'
import { useTablePagination } from '../useTablePagination'

const types = ref([])
const items = ref([])
const loading = ref(false)
const itemLoading = ref(false)
const selectedType = ref(null)
const typeKeyword = ref('')
const typeVisible = ref(false)
const itemVisible = ref(false)
const editingTypeId = ref(null)
const editingItemId = ref(null)
const typeFormRef = ref()
const itemFormRef = ref()
const typeForm = reactive({ code: '', name: '', description: '', sortOrder: 100, enabled: true })
const itemForm = reactive({ code: '', label: '', value: '', cssType: '', sortOrder: 100, enabled: true })
const typeRules = {
  code: [{ required: true, pattern: /^[A-Z][A-Z0-9_]{1,63}$/, message: '大写字母开头的编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入类型名称', trigger: 'blur' }]
}
const itemRules = {
  code: [{ required: true, pattern: /^[A-Z][A-Z0-9_]{0,63}$/, message: '大写字母开头的码值', trigger: 'blur' }],
  label: [{ required: true, message: '请输入显示名称', trigger: 'blur' }]
}
const cssOptions = [
  { label: '默认', value: '' },
  { label: '成功(success)', value: 'success' },
  { label: '警告(warning)', value: 'warning' },
  { label: '危险(danger)', value: 'danger' },
  { label: '信息(info)', value: 'info' },
  { label: '主色(primary)', value: 'primary' }
]

const filteredTypes = computed(() => {
  const keyword = typeKeyword.value.trim().toLowerCase()
  if (!keyword) return types.value
  return types.value.filter(item =>
    item.name.toLowerCase().includes(keyword) || item.code.toLowerCase().includes(keyword)
  )
})

const stats = computed(() => ({
  total: types.value.length,
  builtin: types.value.filter(item => item.builtIn).length,
  custom: types.value.filter(item => !item.builtIn).length,
  items: items.value.length
}))

const selectedTypeName = computed(() => selectedType.value?.name || '未选择类型')
const {
  page: itemPage, pageSize: itemPageSize, total: itemTotal, paged: pagedItems, onSizeChange: onItemSizeChange
} = useTablePagination(items)

async function loadTypes() {
  loading.value = true
  try {
    types.value = await listDictTypes()
    if (selectedType.value) {
      selectedType.value = types.value.find(item => item.id === selectedType.value.id) || null
    }
    if (!selectedType.value && types.value.length) {
      selectedType.value = types.value[0]
    }
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

async function loadItems() {
  if (!selectedType.value) {
    items.value = []
    return
  }
  itemLoading.value = true
  try {
    items.value = await listDictItems(selectedType.value.code)
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    itemLoading.value = false
  }
}

function selectType(row) {
  selectedType.value = row
}

function openCreateType() {
  editingTypeId.value = null
  Object.assign(typeForm, { code: '', name: '', description: '', sortOrder: 100, enabled: true })
  typeVisible.value = true
}

function openEditType(row) {
  editingTypeId.value = row.id
  Object.assign(typeForm, {
    code: row.code, name: row.name, description: row.description || '',
    sortOrder: row.sortOrder, enabled: row.enabled
  })
  typeVisible.value = true
}

async function saveType() {
  await typeFormRef.value.validate()
  try {
    if (editingTypeId.value) {
      await updateDictType(editingTypeId.value, typeForm)
    } else {
      await createDictType(typeForm)
    }
    typeVisible.value = false
    await loadTypes()
    ElMessage.success('字典类型已保存')
  } catch (error) {
    ElMessage.error(error.message)
  }
}

async function removeType(row) {
  if (row.builtIn) {
    ElMessage.warning('内置字典类型不可删除')
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除字典类型「${row.name}」及其全部码值？`, '删除字典类型', { type: 'warning' })
  } catch { return }
  try {
    await deleteDictType(row.id)
    if (selectedType.value?.id === row.id) selectedType.value = null
    await loadTypes()
    ElMessage.success('字典类型已删除')
  } catch (error) {
    ElMessage.error(error.message)
  }
}

function openCreateItem() {
  if (!selectedType.value) {
    ElMessage.warning('请先选择字典类型')
    return
  }
  editingItemId.value = null
  Object.assign(itemForm, { code: '', label: '', value: '', cssType: '', sortOrder: 100, enabled: true })
  itemVisible.value = true
}

function openEditItem(row) {
  editingItemId.value = row.id
  Object.assign(itemForm, {
    code: row.code, label: row.label, value: row.value || row.code,
    cssType: row.cssType || '', sortOrder: row.sortOrder, enabled: row.enabled
  })
  itemVisible.value = true
}

async function saveItem() {
  await itemFormRef.value.validate()
  try {
    if (editingItemId.value) {
      await updateDictItem(editingItemId.value, itemForm)
    } else {
      await createDictItem(selectedType.value.code, itemForm)
    }
    itemVisible.value = false
    await loadItems()
    ElMessage.success('字典码值已保存')
  } catch (error) {
    ElMessage.error(error.message)
  }
}

async function toggleItem(row, enabled) {
  try {
    Object.assign(row, await setDictItemEnabled(row.id, enabled))
    ElMessage.success(enabled ? '码值已启用' : '码值已停用')
  } catch (error) {
    row.enabled = !enabled
    ElMessage.error(error.message)
  }
}

async function removeItem(row) {
  try {
    await ElMessageBox.confirm(`确认删除码值「${row.label}」？`, '删除字典码值', { type: 'warning' })
  } catch { return }
  try {
    await deleteDictItem(row.id)
    await loadItems()
    ElMessage.success('字典码值已删除')
  } catch (error) {
    ElMessage.error(error.message)
  }
}

watch(selectedType, loadItems)
onMounted(loadTypes)
</script>

<template>
  <section class="management-page dict-page page-fixed-layout">
    <div class="page-fixed-overview">
      <div class="page-heading">
        <div>
          <span class="page-kicker">DATA DICTIONARY</span>
          <h2>数据字典</h2>
          <p>维护下拉选项的字典类型与码值。业务表单通过字典编码拉取启用项，修改后无需改代码即可生效。</p>
        </div>
        <div class="heading-actions">
          <el-button :icon="Refresh" :loading="loading" @click="loadTypes">刷新</el-button>
          <el-button type="primary" :icon="Plus" @click="openCreateType">新增类型</el-button>
        </div>
      </div>

      <div class="agent-metrics" aria-label="字典概览">
        <div><span>字典类型</span><strong>{{ stats.total }}</strong></div>
        <div><span>内置类型</span><strong class="metric-warning">{{ stats.builtin }}</strong></div>
        <div><span>自定义类型</span><strong class="metric-primary">{{ stats.custom }}</strong></div>
        <div><span>当前码值</span><strong class="metric-success">{{ stats.items }}</strong></div>
      </div>
    </div>

    <div class="dict-layout page-fixed-scroll" v-loading="loading">
      <aside class="management-section dict-types">
        <div class="table-heading">
          <div><h3>字典类型</h3><span>共 {{ filteredTypes.length }} 个</span></div>
        </div>
        <el-input
          v-model="typeKeyword"
          clearable
          :prefix-icon="Search"
          placeholder="名称或编码"
          class="type-search"
        />
        <div class="type-list">
          <button
            v-for="row in filteredTypes"
            :key="row.id"
            type="button"
            class="type-list-item"
            :class="{ active: row.id === selectedType?.id }"
            @click="selectType(row)"
          >
            <div class="type-list-main">
              <strong>{{ row.name }}</strong>
              <code>{{ row.code }}</code>
            </div>
            <div class="type-list-meta">
              <el-tag v-if="row.builtIn" size="small" type="warning" effect="plain">内置</el-tag>
              <el-tag v-else size="small" type="info" effect="plain">自定义</el-tag>
              <span v-if="!row.enabled" class="type-disabled">已停用</span>
            </div>
            <div class="type-list-actions" @click.stop>
              <el-button link type="primary" :icon="Edit" @click="openEditType(row)">编辑</el-button>
              <el-button link type="danger" :icon="Delete" :disabled="row.builtIn" @click="removeType(row)">删除</el-button>
            </div>
          </button>
          <el-empty v-if="!filteredTypes.length" description="暂无字典类型" :image-size="64" />
        </div>
      </aside>

      <div class="dict-detail management-section page-fixed-body" v-loading="itemLoading">
        <template v-if="selectedType">
          <div class="table-heading">
            <div>
              <h3>码值 · {{ selectedTypeName }}</h3>
              <span>共 {{ itemTotal }} 条 · {{ selectedType.code }}</span>
            </div>
            <el-button type="primary" :icon="Plus" @click="openCreateItem">新增码值</el-button>
          </div>
          <div class="page-table-scroll">
            <el-table :data="pagedItems" border stripe row-key="id" height="100%" empty-text="该类型暂无码值" class="dict-item-table">
              <el-table-column label="码值" min-width="160">
                <template #default="{ row }">
                  <div class="item-cell">
                    <strong>{{ row.label }}</strong>
                    <code>{{ row.code }}</code>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="取值" width="120" show-overflow-tooltip>
                <template #default="{ row }"><span class="cell-main">{{ row.value || row.code }}</span></template>
              </el-table-column>
              <el-table-column label="样式" width="110" align="center">
                <template #default="{ row }">
                  <el-tag v-if="row.cssType" size="small" :type="row.cssType === 'primary' ? '' : row.cssType">{{ row.cssType }}</el-tag>
                  <span v-else class="muted-text">—</span>
                </template>
              </el-table-column>
              <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
              <el-table-column label="启用" width="80" align="center">
                <template #default="{ row }"><el-switch v-model="row.enabled" @change="toggleItem(row, $event)" /></template>
              </el-table-column>
              <el-table-column label="操作" width="140" fixed="right" align="center">
                <template #default="{ row }">
                  <el-button link type="primary" :icon="Edit" @click="openEditItem(row)">编辑</el-button>
                  <el-button link type="danger" :icon="Delete" @click="removeItem(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
          <div class="table-pagination">
            <el-pagination
              v-model:current-page="itemPage"
              v-model:page-size="itemPageSize"
              background
              layout="total, sizes, prev, pager, next"
              :page-sizes="[20, 50, 100]"
              :total="itemTotal"
              @size-change="onItemSizeChange"
            />
          </div>
        </template>
        <el-empty v-else description="请选择左侧字典类型" />
      </div>
    </div>

    <el-dialog v-model="typeVisible" :title="editingTypeId ? '编辑字典类型' : '新增字典类型'" width="min(560px, 94vw)" destroy-on-close>
      <el-form ref="typeFormRef" :model="typeForm" :rules="typeRules" label-position="top" class="editor-form">
        <el-form-item label="类型编码" prop="code">
          <el-input v-model="typeForm.code" :disabled="!!editingTypeId" placeholder="例如 OPS_PRIORITY" />
          <p class="form-item-desc">业务代码引用的字典类型 key，创建后不可改。</p>
        </el-form-item>
        <el-form-item label="类型名称" prop="name">
          <el-input v-model="typeForm.name" placeholder="例如 运维任务优先级" />
          <p class="form-item-desc">管理界面显示的类型名称。</p>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="typeForm.description" type="textarea" :rows="2" />
          <p class="form-item-desc">可选，说明该字典在哪些场景使用。</p>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="typeForm.sortOrder" :min="0" :max="9999" />
          <p class="form-item-desc">类型列表中的显示顺序，越小越靠前。</p>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="typeForm.enabled" />
          <p class="form-item-desc">停用后前端下拉等将不再加载该类型。</p>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="typeVisible = false">取消</el-button>
        <el-button type="primary" @click="saveType">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="itemVisible" :title="editingItemId ? '编辑字典码值' : '新增字典码值'" width="min(560px, 94vw)" destroy-on-close>
      <el-form ref="itemFormRef" :model="itemForm" :rules="itemRules" label-position="top" class="editor-form">
        <el-form-item label="码值编码" prop="code">
          <el-input v-model="itemForm.code" :disabled="!!editingItemId" placeholder="例如 P0" />
          <p class="form-item-desc">条目唯一编码，通常与业务枚举一致。</p>
        </el-form-item>
        <el-form-item label="显示名称" prop="label">
          <el-input v-model="itemForm.label" placeholder="界面展示文案" />
          <p class="form-item-desc">下拉与标签上展示给用户的文字。</p>
        </el-form-item>
        <el-form-item label="实际取值">
          <el-input v-model="itemForm.value" placeholder="默认与编码相同" />
          <p class="form-item-desc">提交给后端的值；多数情况与编码相同。</p>
        </el-form-item>
        <el-form-item label="标签样式">
          <el-select v-model="itemForm.cssType" class="full-width" clearable><el-option v-for="opt in cssOptions" :key="opt.value || 'empty'" :label="opt.label" :value="opt.value" /></el-select>
          <p class="form-item-desc">列表 Tag 颜色样式，可选。</p>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="itemForm.sortOrder" :min="0" :max="9999" />
          <p class="form-item-desc">同类型内选项顺序，越小越靠前。</p>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="itemForm.enabled" />
          <p class="form-item-desc">停用后下拉中不再出现该选项。</p>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="itemVisible = false">取消</el-button>
        <el-button type="primary" @click="saveItem">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.dict-layout {
  display: grid;
  grid-template-columns: minmax(260px, 300px) minmax(0, 1fr);
  gap: 16px;
  align-items: stretch;
  min-height: 0;
}

.dict-layout .management-section { margin-bottom: 0; }

.dict-types {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.type-search { margin-bottom: 12px; flex: 0 0 auto; }

.type-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1 1 auto;
  min-height: 0;
  overflow: auto;
}

.type-list-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
  padding: 12px 14px;
  border: 1px solid #d9e2ec;
  background: #fff;
  text-align: left;
  cursor: pointer;
  font: inherit;
  transition: border-color .15s, background .15s;
}

.type-list-item:hover { border-color: #9fb3c8; }

.type-list-item.active {
  border-color: #147d92;
  background: #e6f6fb;
}

.type-list-main {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.type-list-main strong {
  color: #243b53;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.3;
}

.type-list-main code {
  width: fit-content;
  padding: 1px 6px;
  border-radius: 3px;
  background: #f0f4f8;
  color: #147d92;
  font-size: 11px;
  line-height: 1.5;
}

.type-list-item.active .type-list-main code {
  background: #d9f0f6;
}

.type-list-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.type-disabled {
  color: #829ab1;
  font-size: 12px;
}

.type-list-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 2px;
  padding-top: 8px;
  border-top: 1px solid #e4e7ed;
}

.type-list-item.active .type-list-actions {
  border-top-color: #c5e8f0;
}

.dict-detail {
  min-width: 0;
}

.item-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.item-cell strong {
  color: #243b53;
  font-size: 13px;
  font-weight: 600;
}

.item-cell code {
  width: fit-content;
  padding: 1px 6px;
  border-radius: 3px;
  background: #f0f4f8;
  color: #147d92;
  font-size: 11px;
}

.cell-main {
  color: #334e68;
  font-size: 13px;
}

.dict-item-table :deep(.el-table__cell) {
  padding: 10px 0;
}

@media (max-width: 960px) {
  .dict-layout { grid-template-columns: 1fr; }
  .type-list { max-height: 280px; }
}
</style>
