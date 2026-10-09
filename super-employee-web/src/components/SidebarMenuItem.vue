<!-- @author yangdb -->
<script setup>
import { computed } from 'vue'
import {
  Box, CircleCheck, Coin, Cpu, Document, Files, Folder, Grid, Key, List, Lock,
  Menu as MenuIcon, Monitor, Operation, Platform, Setting, Stamp, Tools, User
} from '@element-plus/icons-vue'

const props = defineProps({ item: { type: Object, required: true } })
const icons = {
  Box, CircleCheck, Coin, Cpu, Document, Files, Folder, Grid, Key, List, Lock,
  Menu: MenuIcon, Monitor, Operation, Platform, Setting, Stamp, Tools, User
}
const CODE_ICONS = {
  SYS_MANAGEMENT: Setting,
  SYS_USER: User,
  SYS_ROLE: Stamp,
  SYS_MENU: MenuIcon
}
const iconComponent = computed(() => CODE_ICONS[props.item.code] || icons[props.item.icon] || Grid)
</script>

<template>
  <el-sub-menu v-if="item.children?.length" :index="item.code">
    <template #title>
      <el-icon><component :is="iconComponent" /></el-icon>
      <span>{{ item.name }}</span>
    </template>
    <SidebarMenuItem v-for="child in item.children" :key="child.id" :item="child" />
  </el-sub-menu>
  <el-menu-item v-else :index="item.code">
    <el-icon><component :is="iconComponent" /></el-icon>
    <template #title>{{ item.name }}</template>
  </el-menu-item>
</template>
