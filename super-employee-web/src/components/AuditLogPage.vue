<!-- @author yangdb -->
<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { listAuditLogs } from '../api'
import { useTablePagination } from '../useTablePagination'

const items = ref([])
const loading = ref(false)
const filters = reactive({ keyword: '', action: '', resource: '' })
const actions = computed(() => [...new Set(items.value.map(item => item.actionName))])
const resources = computed(() => [...new Set(items.value.map(item => item.resourceType))])
const filtered = computed(() => items.value.filter(item => {
  const keyword = filters.keyword.trim().toLowerCase()
  return (!keyword || item.username.toLowerCase().includes(keyword) || (item.detail || '').toLowerCase().includes(keyword))
    && (!filters.action || item.actionName === filters.action)
    && (!filters.resource || item.resourceType === filters.resource)
}))
const { page, pageSize, total, paged, onSizeChange } = useTablePagination(filtered)

async function load() {
  loading.value = true
  try { items.value = await listAuditLogs() }
  catch (error) { ElMessage.error(error.message) }
  finally { loading.value = false }
}
function reset() { Object.assign(filters, { keyword: '', action: '', resource: '' }) }
onMounted(load)
</script>

<template>
  <section class="management-page page-fixed-layout">
    <div class="page-fixed-overview">
      <div class="page-heading">
        <div>
          <span class="page-kicker">SECURITY & COMPLIANCE</span>
          <h2>审计日志</h2>
          <p>查询关键配置、授权、审批和 Agent 管理操作，支持事后追溯。</p>
        </div>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </div>
    </div>
    <div class="management-section page-fixed-body">
      <el-form inline class="query-form">
        <el-form-item label="关键词"><el-input v-model="filters.keyword" clearable :prefix-icon="Search" placeholder="账号或操作详情" /></el-form-item>
        <el-form-item label="动作"><el-select v-model="filters.action" clearable placeholder="全部动作"><el-option v-for="item in actions" :key="item" :label="item" :value="item" /></el-select></el-form-item>
        <el-form-item label="资源"><el-select v-model="filters.resource" clearable placeholder="全部资源"><el-option v-for="item in resources" :key="item" :label="item" :value="item" /></el-select></el-form-item>
        <el-form-item class="query-actions">
          <el-button type="primary" :icon="Search">查询</el-button>
          <el-button :icon="Refresh" @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
      <div class="table-heading"><div><h3>操作记录</h3><span>共 {{ total }} 条记录</span></div></div>
      <div class="page-table-scroll">
        <el-table v-loading="loading" :data="paged" border stripe row-key="id" height="100%" empty-text="暂无审计日志">
          <el-table-column prop="eventTime" label="发生时间" min-width="180" />
          <el-table-column prop="username" label="操作账号" width="130" />
          <el-table-column prop="actionName" label="动作" width="120"><template #default="{ row }"><code>{{ row.actionName }}</code></template></el-table-column>
          <el-table-column prop="resourceType" label="资源类型" width="130" />
          <el-table-column prop="resourceId" label="资源 ID" min-width="190" show-overflow-tooltip />
          <el-table-column label="结果" width="90"><template #default="{ row }"><el-tag :type="row.resultStatus === 'SUCCESS' ? 'success' : 'danger'">{{ row.resultStatus }}</el-tag></template></el-table-column>
          <el-table-column prop="detail" label="详情" min-width="220" show-overflow-tooltip />
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
  </section>
</template>
