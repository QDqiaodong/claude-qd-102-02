<template>
  <div>
    <div class="top">
      <select v-model="statusFilter" class="m">
        <option value="">全部状态</option>
        <option value="未解除">未解除</option>
        <option value="已解除">已解除</option>
      </select>
      <span class="rule">封存即扣在库；专洗走到已完成，才准解除还库</span>
      <span class="grow" />
      <button class="main" @click="openCreate">开污染封存单</button>
    </div>

    <el-table :data="shown" style="width:100%">
      <el-table-column prop="code" label="封存单号" width="110">
        <template #default="{ row }"><span class="mono">{{ row.code }}</span></template>
      </el-table-column>
      <el-table-column label="布草" min-width="170">
        <template #default="{ row }">{{ linenName(row.linenId) }}</template>
      </el-table-column>
      <el-table-column prop="floorCode" label="涉及楼层" width="90" />
      <el-table-column label="封存 / 未解除" width="120">
        <template #default="{ row }">
          <b class="qty">{{ row.quantity }}</b>
          <span class="dim"> / </span>
          <b class="qty" :class="{ active: row.remainQty > 0 }">{{ row.remainQty }}</b>
        </template>
      </el-table-column>
      <el-table-column prop="foundAt" label="发现时刻" width="165" />
      <el-table-column prop="founder" label="发现人" width="90" />
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <span class="st" :class="row.status === '未解除' ? 's-active' : 's-done'">
            {{ row.status }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="专洗 / 解除" min-width="300">
        <template #default="{ row }">
          <template v-if="row.status === '未解除'">
            <span v-if="activeBatch(row)" class="wash">
              专洗 {{ activeBatch(row).code }} · {{ activeBatch(row).status }}
            </span>
            <span v-else-if="finishedBatch(row)" class="wash done">
              专洗 {{ finishedBatch(row).code }} 已完成
            </span>
            <span v-else class="dim">还没挂专洗</span>
            <span class="acts">
              <span v-if="!activeBatch(row) && !finishedBatch(row)" class="lnk" @click="openWash(row)">
                挂专洗
              </span>
              <span
                v-for="(step, idx) in batchSteps(activeBatch(row))"
                :key="idx"
                class="lnk"
                @click="advanceWash(activeBatch(row), step)"
              >{{ step.label }}</span>
              <span v-if="finishedBatch(row)" class="lnk release" @click="doRelease(row)">
                解除并还库 {{ row.remainQty }} 件
              </span>
            </span>
          </template>
          <template v-else>
            <span class="dim">{{ row.releasedAt }} 由 {{ row.releaser }} 解除，件数已还回在库</span>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <div class="foot">
      专洗用的是封存已扣下的件数，不看在库；专洗没走到已完成，封存解不了、件数也回不到可领用。
    </div>

    <el-dialog v-model="createVisible" title="开污染封存单" width="460px">
      <el-alert
        title="单子一落下，这几件立刻从可领用在库里扣掉"
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom:16px"
      />
      <el-form label-width="96px">
        <el-form-item label="在用布草">
          <el-select v-model="form.linenId" filterable style="width:100%" placeholder="只能选在用布草">
            <el-option
              v-for="l in activeLinens"
              :key="l.id"
              :label="`${l.code} ${l.name}（在库 ${l.stock}）`"
              :value="l.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="涉及楼层">
          <el-input v-model="form.floorCode" placeholder="如 感染病房 8F" />
        </el-form-item>
        <el-form-item label="封存件数">
          <el-input-number v-model="form.quantity" :min="1" />
        </el-form-item>
        <el-form-item label="发现时刻">
          <el-date-picker
            v-model="form.foundAt"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="选日期和时刻"
            style="width:100%"
          />
        </el-form-item>
        <el-form-item label="发现人">
          <el-input v-model="form.founder" placeholder="如 张姐" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="danger" @click="saveSeal">封存并扣库</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="washVisible" title="挂专洗批次" width="440px">
      <div class="wash-tip">
        <div>{{ washRow.code }} · {{ linenName(washRow.linenId) }} · {{ washRow.floorCode }}</div>
        <div class="wash-q">
          洗法固定为 <b>专洗</b>，件数恰好 <b>{{ washRow.remainQty }}</b> 件
          （取封存还没解除的件数，不走在库校验）
        </div>
      </div>
      <el-form label-width="96px">
        <el-form-item label="批次号">
          <el-input v-model="washForm.code" placeholder="如 WB-0919" />
        </el-form-item>
        <el-form-item label="送洗日期">
          <el-date-picker v-model="washForm.sendDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="预计回洗">
          <el-date-picker v-model="washForm.expectDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="经办人">
          <el-input v-model="washForm.operator" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="washVisible = false">取消</el-button>
        <el-button type="primary" @click="saveWash">挂批</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { sealApi, batchApi, linenApi } from '../api'

const FLOW = {
  待洗: { label: '开工', action: 'start' },
  洗涤中: { label: '送烘干', action: 'finish' },
  待烘干: { label: '收工', action: 'done' }
}

const rows = ref([])
const batches = ref([])
const linens = ref([])
const statusFilter = ref('未解除')
const createVisible = ref(false)
const washVisible = ref(false)
const form = ref({})
const washRow = ref({})
const washForm = ref({})

const shown = computed(() =>
  rows.value.filter((s) => !statusFilter.value || s.status === statusFilter.value)
)

const activeLinens = computed(() => linens.value.filter((l) => l.status === '在用'))

function linenName(id) {
  const hit = linens.value.find((l) => l.id === id)
  return hit ? `${hit.code} ${hit.name}` : `#${id}`
}

function linkedBatches(row) {
  return batches.value.filter((b) => b.sealId === row.id)
}

function activeBatch(row) {
  return linkedBatches(row).find((b) => b.status !== '已完成') || null
}

function finishedBatch(row) {
  return linkedBatches(row).find((b) => b.status === '已完成') || null
}

function batchSteps(batch) {
  return batch && FLOW[batch.status] ? [FLOW[batch.status]] : []
}

async function load() {
  try {
    rows.value = await sealApi.list({})
    batches.value = await batchApi.list({})
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

function nowText() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

function openCreate() {
  form.value = { quantity: 1, foundAt: nowText() }
  createVisible.value = true
}

async function saveSeal() {
  try {
    const s = await sealApi.create(form.value)
    ElMessage.success(`${s.code} 已封存，${s.quantity} 件已从在库扣掉`)
    createVisible.value = false
    await loadLinens()
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function openWash(row) {
  washRow.value = row
  const today = nowText().slice(0, 10)
  washForm.value = { sendDate: today, expectDate: today, operator: row.founder }
  washVisible.value = true
}

async function saveWash() {
  try {
    const b = await sealApi.specialWash(washRow.value.id, washForm.value)
    ElMessage.success(`已挂专洗 ${b.code}，件数 ${b.quantity}`)
    washVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function advanceWash(batch, step) {
  try {
    await batchApi.advance(batch.id, step.action)
    ElMessage.success('已推进到下一工序')
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function doRelease(row) {
  try {
    const { value: releaser } = await ElMessageBox.prompt(
      `专洗已完成。解除后 ${row.remainQty} 件还回可领用在库，请确认解除人`,
      `解除封存 ${row.code}`,
      { confirmButtonText: '解除并还库', cancelButtonText: '取消', inputValue: row.founder }
    )
    const s = await sealApi.release(row.id, releaser)
    ElMessage.success(`${s.code} 已解除，${s.quantity} 件已还回在库`)
    await loadLinens()
    await load()
  } catch (e) {
    if (e === 'cancel' || e?.message?.includes('cancel')) return
    ElMessage.error(e.message || '已取消')
  }
}

onMounted(async () => {
  await loadLinens()
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
  color: #e6a23c;
}
.grow {
  flex: 1;
}
.main {
  border: none;
  background: #f56c6c;
  color: #fff;
  border-radius: 4px;
  padding: 9px 20px;
  font-size: 13px;
  cursor: pointer;
}
.main:hover {
  filter: brightness(1.06);
}
.mono {
  font-family: monospace;
  color: #606266;
}
.qty {
  font-family: monospace;
  color: #303133;
}
.qty.active {
  color: #f56c6c;
}
.dim {
  color: #a8abb2;
  font-size: 12px;
}
.st {
  font-size: 12px;
  border-radius: 3px;
  padding: 1px 9px;
}
.s-active {
  background: #fef0f0;
  color: #c45656;
  font-weight: 600;
}
.s-done {
  background: #f0f9eb;
  color: #529b2e;
}
.wash {
  font-size: 12px;
  color: #b88230;
  margin-right: 12px;
}
.wash.done {
  color: #529b2e;
}
.acts {
  display: inline-flex;
  gap: 12px;
}
.lnk {
  color: var(--el-color-primary);
  cursor: pointer;
  font-size: 12px;
  user-select: none;
}
.lnk:hover {
  text-decoration: underline;
}
.lnk.release {
  color: #67c23a;
  font-weight: 600;
}
.foot {
  margin-top: 12px;
  font-size: 12px;
  color: #c0c4cc;
}
.wash-tip {
  background: #fdf6ec;
  border-radius: 6px;
  padding: 12px 14px;
  font-size: 13px;
  color: #606266;
  margin-bottom: 14px;
}
.wash-q {
  margin-top: 6px;
  font-size: 12px;
  color: #b88230;
}
.wash-q b {
  color: #c45656;
}
</style>
