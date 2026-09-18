<template>
  <div>
    <div class="top">
      <select v-model="statusFilter" class="m">
        <option value="">全部状态</option>
        <option value="待洗">待洗</option>
        <option value="洗涤中">洗涤中</option>
        <option value="待烘干">待烘干</option>
        <option value="已完成">已完成</option>
      </select>
      <span class="rule">送洗即扣在库；收工按实际回洗件数回库，短少的挂追差</span>
      <span class="grow" />
      <button class="main" @click="openCreate">开一个洗涤批次</button>
    </div>

    <div class="fold">
      <div v-for="g in groups" :key="g.linenId" class="grp">
        <div class="gh" @click="toggle(g.linenId)">
          <span class="arw" :class="{ open: opened.includes(g.linenId) }">›</span>
          <span class="gname">{{ g.name }}</span>
          <span class="gtag">{{ g.rows.length }} 个批次</span>
          <span class="gqty">合计 {{ g.qty }} 件</span>
          <span class="gdots">
            <i v-for="s in statOf(g)" :key="s.k" :class="'d-' + s.k" :title="s.label">{{ s.n }}</i>
          </span>
        </div>

        <div v-show="opened.includes(g.linenId)" class="gb">
          <table class="t">
            <thead>
              <tr>
                <th style="width:110px">批次号</th>
                <th style="width:80px">送洗</th>
                <th style="width:130px">回洗</th>
                <th style="width:110px">送洗日</th>
                <th style="width:110px">预计回</th>
                <th style="width:80px">方式</th>
                <th style="width:90px">状态</th>
                <th style="width:100px">经办</th>
                <th>下一步</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="b in g.rows" :key="b.id">
                <td class="code">{{ b.code }}</td>
                <td>{{ b.quantity }}</td>
                <td>
                  <template v-if="b.returnQty != null">
                    <span class="mono">{{ b.returnQty }}</span>
                    <div v-if="b.returnQty < b.quantity" class="short-tag">
                      短 {{ b.quantity - b.returnQty }} 件 · {{ shortageLabel(b) }}
                    </div>
                  </template>
                  <span v-else class="dim">—</span>
                </td>
                <td class="code">{{ b.sendDate }}</td>
                <td class="code">{{ b.expectDate }}</td>
                <td>
                  <span class="wt" :class="{ special: b.washType === '专洗' }">{{ b.washType }}</span>
                  <div v-if="b.sealId" class="seal-tag">封存 {{ sealCode(b.sealId) }}</div>
                </td>
                <td>
                  <span class="st" :class="'s-' + b.status">{{ b.status }}</span>
                </td>
                <td class="dim">{{ b.operator }}</td>
                <td>
                  <span v-if="next(b.status)" class="lnk" @click="advance(b)">
                    {{ next(b.status).label }}
                  </span>
                  <span v-else class="dim">已完成</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
      <div v-if="!groups.length" class="empty">没有符合条件的批次</div>
    </div>

    <el-dialog v-model="visible" title="开一个洗涤批次" width="470px">
      <el-form label-width="110px">
        <el-form-item label="批次号">
          <el-input v-model="form.code" placeholder="如 WB-0904" />
        </el-form-item>
        <el-form-item label="布草">
          <el-select v-model="form.linenId" style="width:100%">
            <el-option v-for="l in linens" :key="l.id" :label="`${l.code} ${l.name}`" :value="l.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="送洗件数">
          <el-input-number v-model="form.quantity" :min="1" />
        </el-form-item>
        <el-form-item label="送洗日期">
          <el-date-picker v-model="form.sendDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="预计回洗日期">
          <el-date-picker v-model="form.expectDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="洗涤方式">
          <el-select v-model="form.washType" style="width:100%">
            <el-option label="常规" value="常规" />
            <el-option label="强化" value="强化" />
          </el-select>
          <div class="form-note">这里只开常规 / 强化，只认当下在库；提交后送洗件数立刻从在库扣掉，收工按实际回洗件数回库。污染布草的「专洗」要去污染封存页挂批</div>
        </el-form-item>
        <el-form-item label="经办人">
          <el-input v-model="form.operator" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="save">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="doneVisible" title="收工 · 登记回洗" width="480px">
      <el-alert
        v-if="doneShort > 0"
        :title="`回洗少了 ${doneShort} 件：这几件不进在库，当场挂一张追差，结案前楼层领不到`"
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom:16px"
      />
      <el-form label-width="110px">
        <el-form-item label="批次">
          <span class="mono">{{ doneRow.code }}</span>
          <span class="dim" style="margin-left:10px">送洗 {{ doneRow.quantity }} 件</span>
        </el-form-item>
        <el-form-item label="实际回洗件数">
          <el-input-number v-model="doneForm.returnQty" :min="0" :max="doneRow.quantity" />
        </el-form-item>
        <template v-if="doneShort > 0">
          <el-form-item label="责任楼层/工序" required>
            <el-input v-model="doneForm.duty" placeholder="如 8F 或 洗涤厂" />
          </el-form-item>
          <el-form-item label="发现人" required>
            <el-input v-model="doneForm.founder" placeholder="如 孙姐" />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="doneVisible = false">取消</el-button>
        <el-button type="primary" @click="submitDone">
          {{ doneShort > 0 ? `收工并挂追差（短 ${doneShort} 件）` : '收工，全数回库' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { batchApi, linenApi, sealApi, shortageApi } from '../api'

const FLOW = {
  待洗: { label: '开工', action: 'start' },
  洗涤中: { label: '送烘干', action: 'finish' },
  待烘干: { label: '收工', action: 'done' }
}

const rows = ref([])
const linens = ref([])
const seals = ref([])
const shortages = ref([])
const statusFilter = ref('')
const opened = ref([])
const visible = ref(false)
const form = ref({})
const doneVisible = ref(false)
const doneRow = ref({})
const doneForm = ref({})

const doneShort = computed(() =>
  doneRow.value.quantity != null && doneForm.value.returnQty != null
    ? doneRow.value.quantity - doneForm.value.returnQty
    : 0
)

function sealCode(sealId) {
  const hit = seals.value.find((s) => s.id === sealId)
  return hit ? hit.code : `#${sealId}`
}

function shortageOf(batchId) {
  return shortages.value.find((s) => s.batchId === batchId) || null
}

function shortageLabel(b) {
  const s = shortageOf(b.id)
  if (!s) return '追差'
  return s.status === '未结案' ? `追差 ${s.code} 未结案` : `追差 ${s.code} 已结案`
}

const filtered = computed(() =>
  rows.value.filter((b) => !statusFilter.value || b.status === statusFilter.value)
)

const groups = computed(() => {
  const map = new Map()
  for (const b of filtered.value) {
    if (!map.has(b.linenId)) map.set(b.linenId, [])
    map.get(b.linenId).push(b)
  }
  return [...map.entries()].map(([linenId, list]) => ({
    linenId,
    name: linenName(linenId),
    rows: list,
    qty: list.reduce((s, b) => s + (b.quantity || 0), 0)
  }))
})

function linenName(id) {
  const hit = linens.value.find((l) => l.id === id)
  return hit ? `${hit.code} ${hit.name}` : `#${id}`
}

function next(status) {
  return FLOW[status] || null
}

function statOf(g) {
  const keys = [
    { k: '待洗', label: '待洗' },
    { k: '洗涤中', label: '洗涤中' },
    { k: '待烘干', label: '待烘干' },
    { k: '已完成', label: '已完成' }
  ]
  return keys
    .map((x) => ({ ...x, n: g.rows.filter((b) => b.status === x.k).length }))
    .filter((x) => x.n > 0)
}

function toggle(id) {
  const i = opened.value.indexOf(id)
  if (i >= 0) opened.value.splice(i, 1)
  else opened.value.push(id)
}

async function load() {
  try {
    rows.value = await batchApi.list({})
    shortages.value = await shortageApi.list({})
    if (!opened.value.length) opened.value = [...new Set(rows.value.map((b) => b.linenId))]
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function loadLinens() {
  try {
    linens.value = await linenApi.list({})
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function openCreate() {
  form.value = { washType: '常规' }
  visible.value = true
}

async function save() {
  try {
    await batchApi.create(form.value)
    ElMessage.success('已开批次，送洗件数已从在库扣掉')
    visible.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function advance(b) {
  const step = next(b.status)
  if (!step) return
  if (step.action === 'done') {
    if (b.washType === '专洗') {
      await doAdvance(b.id, 'done')
      return
    }
    doneRow.value = b
    doneForm.value = { returnQty: b.quantity, duty: '', founder: '' }
    doneVisible.value = true
    return
  }
  await doAdvance(b.id, step.action)
}

async function doAdvance(id, action, payload) {
  try {
    await batchApi.advance(id, action, payload)
    ElMessage.success('已推进')
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function submitDone() {
  const payload = { returnQty: doneForm.value.returnQty }
  if (doneShort.value > 0) {
    payload.duty = doneForm.value.duty
    payload.founder = doneForm.value.founder
  }
  try {
    await batchApi.advance(doneRow.value.id, 'done', payload)
    ElMessage.success(
      doneShort.value > 0
        ? `已收工：回库 ${doneForm.value.returnQty} 件，短少 ${doneShort.value} 件已挂追差`
        : '已收工，全数回库'
    )
    doneVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(async () => {
  await loadLinens()
  seals.value = await sealApi.list({})
  await load()
})
</script>

<style scoped>
.top {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
.m {
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  padding: 8px 10px;
  font-size: 13px;
  outline: none;
  background: #fff;
}
.rule {
  font-size: 12px;
  color: #b88230;
}
.grow {
  flex: 1;
}
.main {
  border: none;
  background: var(--el-color-primary);
  color: #fff;
  border-radius: 4px;
  padding: 9px 20px;
  font-size: 13px;
  cursor: pointer;
}
.main:hover {
  filter: brightness(1.08);
}
.fold {
  border-top: 1px solid #ebeef5;
}
.grp {
  border-bottom: 1px solid #ebeef5;
}
.gh {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 13px 6px;
  cursor: pointer;
  user-select: none;
}
.gh:hover {
  background: #fafcff;
}
.arw {
  display: inline-block;
  font-size: 18px;
  color: #c0c4cc;
  transform: rotate(0deg);
  transition: transform 0.18s;
  width: 14px;
}
.arw.open {
  transform: rotate(90deg);
}
.gname {
  font-size: 14px;
  font-weight: 600;
}
.gtag {
  font-size: 12px;
  color: #909399;
}
.gqty {
  font-size: 12px;
  color: var(--el-color-primary);
  font-family: monospace;
}
.gdots {
  margin-left: auto;
  display: flex;
  gap: 6px;
}
.gdots i {
  font-style: normal;
  font-size: 11px;
  border-radius: 9px;
  padding: 1px 8px;
  color: #fff;
}
.d-待洗 {
  background: #a0cfff;
}
.d-洗涤中 {
  background: #409eff;
}
.d-待烘干 {
  background: #e6a23c;
}
.d-已完成 {
  background: #b8c2cc;
}
.gb {
  padding: 0 6px 16px 30px;
}
.t {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.t th {
  text-align: left;
  font-size: 12px;
  color: #909399;
  font-weight: 500;
  padding: 7px 9px;
  border-bottom: 1px solid #ebeef5;
}
.t td {
  padding: 9px;
  border-bottom: 1px solid #f5f7fa;
}
.code {
  font-family: monospace;
  color: #606266;
}
.mono {
  font-family: monospace;
}
.wt.special {
  color: #c45656;
  font-weight: 600;
}
.seal-tag {
  font-size: 11px;
  color: #c45656;
  font-family: monospace;
  margin-top: 2px;
}
.short-tag {
  font-size: 11px;
  color: #c45656;
  margin-top: 2px;
}
.form-note {
  font-size: 11px;
  color: #c0c4cc;
  line-height: 1.5;
  margin-top: 4px;
}
.dim {
  color: #a8abb2;
  font-size: 12px;
}
.st {
  font-size: 12px;
  border-radius: 3px;
  padding: 1px 8px;
}
.s-待洗 {
  background: #ecf5ff;
  color: #337ecc;
}
.s-洗涤中 {
  background: #e8f3ff;
  color: #1d6fd4;
  font-weight: 600;
}
.s-待烘干 {
  background: #fdf6ec;
  color: #b88230;
  font-weight: 600;
}
.s-已完成 {
  background: #f0f9eb;
  color: #529b2e;
}
.lnk {
  color: var(--el-color-primary);
  cursor: pointer;
  font-size: 12px;
}
.lnk:hover {
  text-decoration: underline;
}
.empty {
  text-align: center;
  color: #c0c4cc;
  padding: 50px 0;
  font-size: 13px;
}
</style>
