/** @author yangdb */
import { computed, ref, watch } from 'vue'

/**
 * 前端列表分页：对已过滤的数据源做切片。
 * @param {import('vue').Ref|import('vue').ComputedRef} source
 * @param {number} defaultSize
 */
export function useTablePagination(source, defaultSize = 20) {
  const page = ref(1)
  const pageSize = ref(defaultSize)
  const total = computed(() => (source.value || []).length)
  const paged = computed(() => {
    const list = source.value || []
    const start = (page.value - 1) * pageSize.value
    return list.slice(start, start + pageSize.value)
  })

  watch(source, () => {
    page.value = 1
  })

  function onSizeChange() {
    page.value = 1
  }

  return { page, pageSize, total, paged, onSizeChange }
}
