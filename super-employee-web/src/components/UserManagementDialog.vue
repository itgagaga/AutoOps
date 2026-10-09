<!-- @author yangdb -->
<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { assignUserRole, createUser, listRoles, listUsers, setUserEnabled } from '../api'
import { useTablePagination } from '../useTablePagination'

const users = ref([])
const roles = ref([])
const loading = ref(false)
const editorVisible = ref(false)
const formRef = ref()
const form = reactive({ username: '', password: '', displayName: '', role: 'OPERATOR' })
const filters = reactive({ keyword: '', role: '', enabled: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, min: 8, message: '密码至少 8 位', trigger: 'blur' }],
  displayName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }]
}

const filteredUsers = computed(() => users.value.filter(user => {
  const keyword = filters.keyword.trim().toLowerCase()
  const matchesKeyword = !keyword || user.username.toLowerCase().includes(keyword) || user.displayName.toLowerCase().includes(keyword)
  const matchesRole = !filters.role || user.role === filters.role
  const matchesStatus = filters.enabled === '' || user.enabled === filters.enabled
  return matchesKeyword && matchesRole && matchesStatus
}))
const { page, pageSize, total, paged, onSizeChange } = useTablePagination(filteredUsers)

async function load() {
  loading.value = true
  try {
    const [userList, roleList] = await Promise.all([listUsers(), listRoles()])
    users.value = userList
    roles.value = roleList.filter(item => item.enabled)
    if (!roles.value.some(item => item.code === form.role) && roles.value.length) form.role = roles.value[0].code
  } catch (error) { ElMessage.error(error.message) }
  finally { loading.value = false }
}

async function addUser() {
  await formRef.value.validate()
  try {
    await createUser(form)
    Object.assign(form, { username: '', password: '', displayName: '', role: roles.value[0]?.code || '' })
    formRef.value.resetFields()
    editorVisible.value = false
    await load()
    ElMessage.success('用户已创建')
  } catch (error) { ElMessage.error(error.message) }
}

function openCreate() {
  Object.assign(form, { username: '', password: '', displayName: '', role: roles.value[0]?.code || '' })
  formRef.value?.clearValidate()
  editorVisible.value = true
}

function resetFilters() {
  Object.assign(filters, { keyword: '', role: '', enabled: '' })
}

async function changeRole(user, role) {
  try {
    await assignUserRole(user.id, role)
    user.role = role
    ElMessage.success('角色已更新')
  } catch (error) { await load(); ElMessage.error(error.message) }
}

async function toggle(user, enabled) {
  try {
    const updated = await setUserEnabled(user.id, enabled)
    Object.assign(user, updated)
    ElMessage.success(enabled ? '用户已启用' : '用户已停用')
  } catch (error) {
    user.enabled = !enabled
    ElMessage.error(error.message)
  }
}

onMounted(load)
</script>

<template>
  <section class="management-page page-fixed-layout">
    <div class="page-fixed-overview">
      <div class="page-heading"><div><span class="page-kicker">SYSTEM MANAGEMENT</span><h2>用户管理</h2><p>维护登录用户及其所属角色。</p></div><el-button type="primary" :icon="Plus" @click="openCreate">新增用户</el-button></div>
    </div>
    <div class="management-section page-fixed-body">
      <el-form :model="filters" inline class="query-form">
        <el-form-item label="关键词"><el-input v-model="filters.keyword" clearable placeholder="用户名或姓名" :prefix-icon="Search" /></el-form-item>
        <el-form-item label="角色"><el-select v-model="filters.role" clearable placeholder="全部角色"><el-option v-for="role in roles" :key="role.id" :label="role.name" :value="role.code" /></el-select></el-form-item>
        <el-form-item label="状态"><el-select v-model="filters.enabled" clearable placeholder="全部状态"><el-option label="启用" :value="true" /><el-option label="停用" :value="false" /></el-select></el-form-item>
        <el-form-item class="query-actions"><el-button type="primary" :icon="Search">查询</el-button><el-button :icon="Refresh" @click="resetFilters">重置</el-button></el-form-item>
      </el-form>
      <div class="table-heading"><div><h3>用户列表</h3><span>共 {{ total }} 条记录</span></div></div>
      <div class="page-table-scroll">
        <el-table v-loading="loading" :data="paged" border stripe height="100%">
          <el-table-column prop="username" label="用户名" min-width="130" />
          <el-table-column prop="displayName" label="姓名" min-width="150" />
          <el-table-column label="角色" min-width="180">
            <template #default="{ row }">
              <el-select :model-value="row.role" @change="changeRole(row, $event)">
                <el-option v-for="role in roles" :key="role.id" :label="role.name" :value="role.code" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="启用" width="88">
            <template #default="{ row }"><el-switch v-model="row.enabled" @change="value => toggle(row, value)" /></template>
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
    </div>

    <el-dialog v-model="editorVisible" title="新增用户" width="min(620px, 94vw)" destroy-on-close>
      <p class="editor-hint">设置用户的初始登录信息和所属角色。新建默认启用，可在列表中切换启用状态。</p>
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="editor-form">
      <el-row :gutter="12">
        <el-col :xs="24" :sm="12">
          <el-form-item label="用户名" prop="username">
            <el-input v-model="form.username" placeholder="请输入登录用户名" />
            <p class="form-item-desc">登录账号，建议使用工号或拼音，创建后一般不改。</p>
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="姓名" prop="displayName">
            <el-input v-model="form.displayName" placeholder="请输入用户姓名" />
            <p class="form-item-desc">界面展示的真实姓名或昵称。</p>
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="初始密码" prop="password">
            <el-input v-model="form.password" type="password" show-password placeholder="至少 8 位" />
            <p class="form-item-desc">至少 8 位；首次登录后建议用户自行修改。</p>
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="角色" prop="role">
            <el-select v-model="form.role" class="full-width"><el-option v-for="role in roles" :key="role.id" :label="role.name" :value="role.code" /></el-select>
            <p class="form-item-desc">决定菜单权限与可执行操作范围。</p>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
      <template #footer><el-button @click="editorVisible = false">取消</el-button><el-button type="primary" @click="addUser">确认创建</el-button></template>
    </el-dialog>
  </section>
</template>
