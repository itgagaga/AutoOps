<!-- @author yangdb -->
<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Folder, Refresh } from '@element-plus/icons-vue'
import { listDeploymentDirectories } from '../api'

const props = defineProps({
  hostId: { type: String, default: '' },
  disabled: { type: Boolean, default: false }
})
const emit = defineEmits(['select'])

const visible = ref(false)
const loading = ref(false)
const currentPath = ref('/')
const parentPath = ref(null)
const entries = ref([])

/** 打开受管主机目录选择器，并从根目录开始浏览。 */
async function open() {
  if (!props.hostId) {
    ElMessage.warning('请先选择目标主机')
    return
  }
  visible.value = true
  await load('/')
}

/** 读取当前主机的远端子目录。 */
async function load(path = currentPath.value) {
  loading.value = true
  try {
    const result = await listDeploymentDirectories(props.hostId, path)
    currentPath.value = result.path || '/'
    parentPath.value = result.parentPath
    entries.value = result.entries || []
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

/** 将当前目录追加到候选列表，根目录不允许作为部署目标。 */
function choose(path = currentPath.value) {
  if (!path || path === '/') {
    ElMessage.warning('不能将服务器根目录作为部署候选目录')
    return
  }
  emit('select', path)
  visible.value = false
}
</script>

<template>
  <el-button :icon="Folder" :disabled="disabled || !hostId" @click="open">连接服务器选择</el-button>
  <el-dialog v-model="visible" title="选择服务器候选部署目录" width="min(760px, 94vw)" append-to-body destroy-on-close>
    <div class="remote-picker-toolbar">
      <el-button :icon="ArrowLeft" :disabled="!parentPath" @click="load(parentPath)">上级</el-button>
      <el-button :icon="Refresh" :loading="loading" @click="load()">刷新</el-button>
      <span>当前路径：<code>{{ currentPath }}</code></span>
    </div>
    <el-table v-loading="loading" :data="entries" height="380" border empty-text="当前目录没有可访问的子目录" @row-dblclick="row => load(row.path)">
      <el-table-column label="目录名称" min-width="300">
        <template #default="{ row }">
          <el-button link type="primary" :icon="Folder" @click="load(row.path)">{{ row.name }}</el-button>
        </template>
      </el-table-column>
      <el-table-column label="路径" min-width="320" prop="path" show-overflow-tooltip />
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }"><el-button link type="primary" @click="choose(row.path)">选择</el-button></template>
      </el-table-column>
    </el-table>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :disabled="currentPath === '/'" @click="choose()">选择当前目录</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.remote-picker-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}
.remote-picker-toolbar span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
