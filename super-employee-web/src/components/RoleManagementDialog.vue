<!-- @author yangdb -->
<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Edit, Key, Plus } from '@element-plus/icons-vue'
import { createRole, listMenus, listRoles, setRoleMenus, updateRole } from '../api'
import { useTablePagination } from '../useTablePagination'

const emit = defineEmits(['changed'])
const roles = ref([])
const menus = ref([])
const loading = ref(false)
const editorVisible = ref(false)
const permissionVisible = ref(false)
const editingId = ref(null)
const permissionRole = ref(null)
const treeRef = ref()
const formRef = ref()
const form = reactive({ code: '', name: '', description: '' })
const rules = {
  code: [{ required: true, pattern: /^[A-Z][A-Z0-9_]{1,31}$/, message: '使用大写字母、数字和下划线', trigger: 'blur' }],
  name: [{ required: true, message: '请输入角色名称', trigger: 'blur' }]
}

const menuTree = computed(() => {
  const nodes = menus.value.map(item => ({ ...item, children: [] }))
  const byId = new Map(nodes.map(item => [item.id, item]))
  const roots = []
  nodes.forEach(item => { if (item.parentId && byId.has(item.parentId)) byId.get(item.parentId).children.push(item); else roots.push(item) })
  return roots
})
const { page, pageSize, total, paged, onSizeChange } = useTablePagination(roles)

async function load() {
  loading.value = true
  try { [roles.value, menus.value] = await Promise.all([listRoles(), listMenus()]) }
  catch (error) { ElMessage.error(error.message) }
  finally { loading.value = false }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, { code: '', name: '', description: '' })
  editorVisible.value = true
}

function openEdit(role) {
  editingId.value = role.id
  Object.assign(form, { code: role.code, name: role.name, description: role.description || '' })
  editorVisible.value = true
}

async function saveRole() {
  await formRef.value.validate()
  try {
    const current = roles.value.find(item => item.id === editingId.value)
    const payload = {
      ...form,
      enabled: editingId.value ? (current?.enabled ?? true) : true
    }
    if (editingId.value) await updateRole(editingId.value, payload); else await createRole(payload)
    editorVisible.value = false
    await load(); emit('changed'); ElMessage.success('角色已保存')
  } catch (error) { ElMessage.error(error.message) }
}

async function toggle(role, enabled) {
  try {
    await updateRole(role.id, {
      code: role.code,
      name: role.name,
      description: role.description || '',
      enabled
    })
    role.enabled = enabled
    emit('changed')
    ElMessage.success(enabled ? '角色已启用' : '角色已停用')
  } catch (error) {
    role.enabled = !enabled
    ElMessage.error(error.message)
  }
}

async function openPermissions(role) {
  permissionRole.value = role
  permissionVisible.value = true
  await nextTick()
  treeRef.value.setCheckedKeys(role.menuIds || [])
}

async function savePermissions() {
  const ids = [...treeRef.value.getCheckedKeys(), ...treeRef.value.getHalfCheckedKeys()]
  try {
    await setRoleMenus(permissionRole.value.id, [...new Set(ids)])
    permissionVisible.value = false
    await load(); emit('changed'); ElMessage.success('菜单权限已更新')
  } catch (error) { ElMessage.error(error.message) }
}

onMounted(load)
</script>

<template>
  <section class="management-page page-fixed-layout">
    <div class="page-fixed-overview">
      <div class="page-heading"><div><span class="page-kicker">SYSTEM MANAGEMENT</span><h2>角色管理</h2><p>配置角色信息，并为角色分配可访问菜单。</p></div><el-button type="primary" :icon="Plus" @click="openCreate">新建角色</el-button></div>
    </div>
    <div class="management-section page-fixed-body">
      <div class="table-heading"><div><h3>角色列表</h3><span>共 {{ total }} 条记录</span></div></div>
      <div class="page-table-scroll">
        <el-table v-loading="loading" :data="paged" border height="100%">
          <el-table-column prop="code" label="角色编码" min-width="140" />
          <el-table-column prop="name" label="角色名称" min-width="130" />
          <el-table-column prop="description" label="说明" min-width="200" />
          <el-table-column label="启用" width="88"><template #default="{ row }"><el-switch v-model="row.enabled" :disabled="row.builtIn" @change="value => toggle(row, value)" /></template></el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <div class="table-actions">
                <el-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
                <el-button link type="primary" :icon="Key" @click="openPermissions(row)">菜单权限</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
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

    <el-dialog v-model="editorVisible" :title="editingId ? '编辑角色' : '新建角色'" width="480px" append-to-body>
      <p class="editor-hint">新建默认启用，可在列表中切换启用状态。</p>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="editor-form">
        <el-form-item label="角色编码" prop="code">
          <el-input v-model="form.code" :disabled="Boolean(editingId)" placeholder="例如：AUDITOR" />
          <p class="form-item-desc">系统内唯一标识，创建后不可改。</p>
        </el-form-item>
        <el-form-item label="角色名称" prop="name">
          <el-input v-model="form.name" />
          <p class="form-item-desc">分配用户时显示的名称。</p>
        </el-form-item>
        <el-form-item label="角色说明">
          <el-input v-model="form.description" type="textarea" :rows="3" />
          <p class="form-item-desc">简述职责与适用岗位，便于管理员选型。</p>
        </el-form-item>
      </el-form>
      <template #footer><el-button @click="editorVisible = false">取消</el-button><el-button type="primary" @click="saveRole">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="permissionVisible" :title="`菜单权限 - ${permissionRole?.name || ''}`" width="520px" append-to-body>
      <el-tree ref="treeRef" :data="menuTree" node-key="id" show-checkbox default-expand-all :props="{ label: 'name', children: 'children' }"><template #default="{ data }"><span class="tree-node-label"><span>{{ data.name }}</span><code>{{ data.code }}</code></span></template></el-tree>
      <template #footer><el-button @click="permissionVisible = false">取消</el-button><el-button type="primary" @click="savePermissions">保存权限</el-button></template>
    </el-dialog>
    </div>
  </section>
</template>
