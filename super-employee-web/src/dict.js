/** @author yangdb */
export function toLabelMap(options = []) {
  return Object.fromEntries(options.map(item => [item.value || item.code, item.label]))
}

export function toCssMap(options = []) {
  return Object.fromEntries(options.filter(item => item.cssType).map(item => [item.value || item.code, item.cssType]))
}

export function optionValue(item) {
  return item?.value || item?.code
}
