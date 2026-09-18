<template>
  <div>
    <div class="top">
      <input v-model="keyword" class="s" placeholder="搜名称 / 编号" />
      <select v-model="cat" class="m">
        <option value="">全部类别</option>
        <option v-for="c in categories" :key="c" :value="c">{{ c }}</option>
      </select>
      <span class="grow" />
      <button class="round" title="新增布草" @click="openCreate">＋</button>
    </div>

    <div class="grid">
      <div
        v-for="l in shown"
        :key="l.id"
        class="card"
        :class="{ low: isLow(l), off: l.status === '停用' }"
      >
        <div class="cap">
          <span class="nm">{{ l.name }}</span>
          <span class="ct">{{ l.category }}</span>
        </div>
        <div class="sub">{{ l.code }}<span class="dot">·</span>{{ l.spec }}</div>

        <div class="num">
          <b>{{ l.stock }}</b>
          <span>件在库</span>
          <em v-if="isLow(l)" class="warn">低于预警 {{ l.warnStock }}</em>
          <em v-else>预警线 {{ l.warnStock }}</em>
        </div>
        <div v-if="sealedOf(l.id) > 0" class="sealed">
          另有 <b>{{ sealedOf(l.id) }}</b> 件污染封存中，不可领用
        </div>

        <div class="meter">
          <i :style="{ width: barWidth(l) }" />
          <u :style="{ left: markLeft(l) }" />
        </div>

        <div class="acts">
          <span @click="openEdit(l)">改</span>
          <span @click="toggle(l)">{{ l.status === '停用' ? '启用' : '停用' }}</span>
        </div>
      </div>
    </div>

    <el-dialog v-model="visible" :title="form.id ? '编辑布草' : '新增布草'" width="440px">
      <el-form label-width="96px">
        <el-form-item label="编号">
          <el-input v-model="form.code" :disabled="!!form.id" placeholder="如 LN-007" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="form.name" placeholder="如 白色浴袍" />
        </el-form-item>
        <el-form-item label="类别">
          <el-select v-model="form.category" style="width:100%">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="规格">
          <el-input v-model="form.spec" placeholder="如 70x140" />
        </el-form-item>
        <el-form-item v-if="!form.id" label="初始在库">
          <el-input-number v-model="form.stock" :min="0" />
        </el-form-item>
        <el-form-item label="预警线">
          <el-input-number v-model="form.warnStock" :min="1" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width:100%">
            <el-option label="在用" value="在用" />
            <el-option label="停用" value="停用" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { linenApi, sealApi } from '../api'

const categories = ['床单', '被套', '枕套', '浴巾', '地巾']
const rows = ref([])
const seals = ref([])
const keyword = ref('')
const cat = ref('')
const visible = ref(false)
const form = ref({})

function sealedOf(linenId) {
  return seals.value
    .filter((s) => s.linenId === linenId && s.status === '未解除')
    .reduce((sum, s) => sum + (s.remainQty || 0), 0)
}

const shown = computed(() => {
  const k = keyword.value.trim()
  return rows.value.filter(
    (l) =>
      (!k || (l.name || '').includes(k) || (l.code || '').includes(k)) &&
      (!cat.value || l.category === cat.value)
  )
})

function isLow(l) {
  return l.stock < l.warnStock
}

function barWidth(l) {
  const full = (l.warnStock || 1) * 3
  return Math.min(100, Math.round((l.stock / full) * 100)) + '%'
}

function markLeft() {
  return '33.3%'
}

async function load() {
  try {
    const [linenRows, sealRows] = await Promise.all([
      linenApi.list({}),
      sealApi.list({ status: '未解除' })
    ])
    rows.value = linenRows
    seals.value = sealRows
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function openCreate() {
  form.value = { category: '床单', stock: 0, warnStock: 50, status: '在用' }
  visible.value = true
}

function openEdit(row) {
  form.value = { ...row }
  visible.value = true
}

async function save() {
  try {
    if (form.value.id) {
      await linenApi.update(form.value.id, form.value)
    } else {
      await linenApi.create(form.value)
    }
    ElMessage.success('已保存')
    visible.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function toggle(row) {
  const next = row.status === '停用' ? '在用' : '停用'
  try {
    await linenApi.update(row.id, { status: next })
    ElMessage.success(next === '停用' ? '已停用' : '已启用')
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(load)
</script>

<style scoped>
.top {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
.s {
  width: 230px;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  padding: 8px 11px;
  font-size: 13px;
  outline: none;
}
.s:focus {
  border-color: var(--el-color-primary);
}
.m {
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  padding: 8px 10px;
  font-size: 13px;
  outline: none;
  background: #fff;
}
.grow {
  flex: 1;
}
.round {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: none;
  background: var(--el-color-primary);
  color: #fff;
  font-size: 19px;
  line-height: 1;
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.35);
}
.round:hover {
  filter: brightness(1.08);
}
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(255px, 1fr));
  gap: 14px;
}
.card {
  position: relative;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 15px 16px 13px;
  transition: box-shadow 0.15s, transform 0.15s;
}
.card:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.07);
  transform: translateY(-1px);
}
.card.low {
  border-color: #fbc4c4;
  background: #fffafa;
}
.card.off {
  opacity: 0.55;
}
.cap {
  display: flex;
  align-items: center;
  gap: 8px;
}
.nm {
  font-size: 15px;
  font-weight: 600;
}
.ct {
  font-size: 11px;
  color: #909399;
  background: #f4f4f5;
  border-radius: 9px;
  padding: 1px 8px;
}
.sub {
  margin-top: 3px;
  font-size: 12px;
  color: #a8abb2;
  font-family: monospace;
}
.dot {
  margin: 0 6px;
}
.num {
  display: flex;
  align-items: baseline;
  gap: 7px;
  margin: 13px 0 7px;
}
.num b {
  font-size: 26px;
  line-height: 1;
  color: var(--el-color-primary);
  font-family: monospace;
}
.card.low .num b {
  color: #f56c6c;
}
.num span {
  font-size: 12px;
  color: #909399;
}
.num em {
  margin-left: auto;
  font-style: normal;
  font-size: 11px;
  color: #c0c4cc;
}
.num em.warn {
  color: #f56c6c;
  font-weight: 600;
}
.sealed {
  margin: 0 0 9px;
  font-size: 12px;
  color: #c45656;
  background: #fef0f0;
  border-radius: 4px;
  padding: 4px 8px;
}
.sealed b {
  font-family: monospace;
}
.meter {
  position: relative;
  height: 6px;
  border-radius: 3px;
  background: #f2f3f5;
  overflow: hidden;
}
.meter i {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  border-radius: 3px;
  background: var(--el-color-primary);
  transition: width 0.3s;
}
.card.low .meter i {
  background: #f56c6c;
}
.meter u {
  position: absolute;
  top: -2px;
  bottom: -2px;
  width: 2px;
  background: #c0c4cc;
}
.acts {
  position: absolute;
  right: 14px;
  bottom: 12px;
  display: flex;
  gap: 12px;
  opacity: 0;
  transition: opacity 0.15s;
}
.card:hover .acts {
  opacity: 1;
}
.acts span {
  font-size: 12px;
  color: var(--el-color-primary);
  cursor: pointer;
  user-select: none;
}
.acts span:hover {
  text-decoration: underline;
}
</style>
