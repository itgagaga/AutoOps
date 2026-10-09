<!-- @author yangdb -->
<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Edit, Plus } from '@element-plus/icons-vue'
import { createMenu, listMenus, updateMenu } from '../api'

const emit = defineEmits(['changed'])
const menus = ref([])
const loading = ref(false)
const editorVisible = ref(false)
const editingId = ref(null)
const formRef = ref()
const form = reactive({ parentId: '', code: '', name: '', path: '', icon: '', sortOrder: 0 })
const rules = {
  code: [{ required: true, pattern: /^[A-Z][A-Z0-9_]{1,47}$/, message: '使用大写字母、数字和下划线', trigger: 'blur' }],
  name: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }]
}

const menuTree = computed(() => {
  const nodes = menus.value.map(item => ({ ...item, children: [] }))
  const byId = new Map(nodes.map(item => [item.id, item]))
  const roots = []
  nodes.forEach(item => { if (item.parentId && byId.has(item.parentId)) byId.get(item.parentId).children.push(item); else roots.push(item) })
  return roots
})

async function load() {
  loading.value = true
  try { menus.value = await listMenus() }
  catch (error) { ElMessage.error(error.message) }
  finally { loading.value = false }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, { parentId: '', code: '', name: '', path: '', icon: '', sortOrder: 0 })
  editorVisible.value = true
}

function openEdit(menu) {
  editingId.value = menu.id
  Object.assign(form, { parentId: menu.parentId || '', code: menu.code, name: menu.name, path: menu.path || '', icon: menu.icon || '', sortOrder: menu.sortOrder })
  editorVisible.value = true
}

async function saveMenu() {
  await formRef.value.validate()
  try {
    const current = menus.value.find(item => item.id === editingId.value)
    const payload = {
      ...form,
      parentId: form.parentId || null,
      enabled: editingId.value ? (current?.enabled ?? true) : true
    }
    if (editingId.value) await updateMenu(editingId.value, payload); else await createMenu(payload)
    editorVisible.value = false
    await load(); emit('changed'); ElMessage.success('菜单已保存')
  } catch (error) { ElMessage.error(error.message) }
}

async function toggle(menu, enabled) {
  try {
    await updateMenu(menu.id, {
      parentId: menu.parentId || null,
      code: menu.code,
      name: menu.name,
      path: menu.path || '',
      icon: menu.icon || '',
      sortOrder: menu.sortOrder,
      enabled
    })
    menu.enabled = enabled
    emit('changed')
    ElMessage.success(enabled ? '菜单已启用' : '菜单已停用')
  } catch (error) {
    menu.enabled = !enabled
    ElMessage.error(error.message)
  }
}

onMounted(load)
</script>

<template>
  <section class="management-page page-fixed-layout">
    <div class="page-fixed-overview">
      <div class="page-heading"><div><span class="page-kicker">SYSTEM MANAGEMENT</span><h2>菜单管理</h2><p>维护菜单层级、权限编码、路由与显示顺序。</p></div><el-button type="primary" :icon="Plus" @click="openCreate">新建菜单</el-button></div>
    </div>
    <div class="management-section page-fixed-body">
      <div class="page-table-scroll">
        <el-table v-loading="loading" :data="menuTree" row-key="id" border default-expand-all height="100%" :tree-props="{ children: 'children' }">
          <el-table-column prop="name" label="菜单名称" min-width="170" />
          <el-table-column prop="code" label="权限编码" min-width="170"><template #default="{ row }"><code>{{ row.code }}</code></template></el-table-column>
          <el-table-column prop="path" label="路由/命令" min-width="160" />
          <el-table-column prop="icon" label="图标" width="110" />
          <el-table-column prop="sortOrder" label="排序" width="80" />
          <el-table-column label="启用" width="88"><template #default="{ row }"><el-switch v-model="row.enabled" @change="value => toggle(row, value)" /></template></el-table-column>
          <el-table-column label="操作" width="90" fixed="right"><template #default="{ row }"><el-button text type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button></template></el-table-column>
        </el-table>
      </div>

    <el-dialog v-model="editorVisible" :title="editingId ? '编辑菜单' : '新建菜单'" width="560px" append-to-body>
      <p class="editor-hint">新建默认启用，可在列表中切换启用状态。</p>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="editor-form">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="菜单编码" prop="code">
              <el-input v-model="form.code" :disabled="Boolean(editingId)" placeholder="例如：SYS_AUDIT" />
              <p class="form-item-desc">权限与路由绑定用的唯一编码。</p>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="菜单名称" prop="name">
              <el-input v-model="form.name" />
              <p class="form-item-desc">侧栏显示的中文名称。</p>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="上级菜单">
              <el-select v-model="form.parentId" clearable class="full-width"><el-option v-for="menu in menus.filter(item => item.id !== editingId)" :key="menu.id" :label="menu.name" :value="menu.id" /></el-select>
              <p class="form-item-desc">留空为顶级菜单；选择后成为其子项。</p>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排序">
              <el-input-number v-model="form.sortOrder" :min="0" :max="9999" class="full-width" />
              <p class="form-item-desc">同级内数字越小越靠前。</p>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="路由/命令">
              <el-input v-model="form.path" placeholder="/system/audit" />
              <p class="form-item-desc">前端路由 path 或内部命令标识。</p>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="图标名称">
              <el-input v-model="form.icon" placeholder="Document" />
              <p class="form-item-desc">Element Plus 图标组件名，如 Document。</p>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer><el-button @click="editorVisible = false">取消</el-button><el-button type="primary" @click="saveMenu">保存</el-button></template>
    </el-dialog>
    </div>
  </section>
</template>
