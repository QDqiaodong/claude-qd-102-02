<template>
  <div>
    <div class="chips">
      <span :class="{ on: status === '' }" @click="status = ''">全部</span>
      <span :class="{ on: status === '未结案' }" @click="status = '未结案'">未结案</span>
      <span :class="{ on: status === '已结案' }" @click="status = '已结案'">已结案</span>
      <span class="rule">短少件数结案前不进在库；结案只两条路：补回入库，或转报损等确认</span>
    </div>

    <el-table :data="shown" :row-class-name="rowClass" style="width:100%">
      <el-table-column prop="code" label="追差单号" width="110">
        <template #default="{ row }"><span class="mono">{{ row.code }}</span></template>
      </el-table-column>
      <el-table-column label="批次" width="110">
        <template #default="{ row }"><span class="mono">{{ batchCode(row.batchId) }}</span></template>
      </el-table-column>
      <el-table-column label="布草" min-width="170">
        <template #default="{ row }">{{ linenName(row.linenId) }}</template>
      </el-table-column>
      <el-table-column label="送洗 / 回洗 / 短少" width="150">
        <template #default="{ row }">
          <span class="mono">{{ row.sendQty }} / {{ row.returnQty }} / </span>
          <b class="short">{{ row.shortQty }}</b>
        </template>
      </el-table-column>
      <el-table-column prop="duty" label="责任楼层/工序" width="120" />
      <el-table-column prop="founder" label="发现人" width="90" />
      <el-table-column prop="foundAt" label="发现时刻" width="165" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <span class="st" :class="row.status === '未结案' ? 's-open' : 's-done'">{{ row.status }}</span>
        </template>
      </el-table-column>
      <el-table-column label="结案 / 操作" min-width="280">
        <template #default="{ row }">
          <template v-if="row.status === '未结案'">
            <template v-if="row.lossId">
              <span class="hint">已转报损 #{{ row.lossId }} 等确认；确认前这 {{ row.shortQty }} 件不进在库</span>
            </template>
            <template v-else>
              <span class="lnk" @click="doReplenish(row)">补回入库 {{ row.shortQty }} 件</span>
              <span class="lnk warn" @click="openLoss(row)">转报损</span>
            </template>
          </template>
          <template v-else>
            <span class="hint done">{{ row.closeType }} · {{ row.closedAt }}</span>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <div class="foot">
      追差在批次收工（回洗少于送洗）时当场挂上；补回入库或报损确认之前，楼层收发领不到短少的件数。
    </div>

    <el-dialog v-model="lossVisible" title="追差转报损" width="440px">
      <el-alert
        :title="`短少 ${lossRow.shortQty} 件转进报损赔付等确认；确认前追差仍未结案，件数不进在库`"
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom:16px"
      />
      <el-form label-width="96px">
        <el-form-item label="追差单">
          <span class="mono">{{ lossRow.code }}</span>
          <span class="dim" style="margin-left:10px">{{ linenName(lossRow.linenId) }}</span>
        </el-form-item>
        <el-form-item label="报损原因">
          <el-select v-model="lossReason" style="width:100%">
            <el-option label="丢失" value="丢失" />
            <el-option label="破损" value="破损" />
            <el-option label="污损" value="污损" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="lossVisible = false">取消</el-button>
        <el-button type="danger" @click="submitLoss">转报损等确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { shortageApi, linenApi, batchApi } from '../api'

const rows = ref([])
const linens = ref([])
const batches = ref([])
const status = ref('')
const lossVisible = ref(false)
const lossRow = ref({})
const lossReason = ref('丢失')

const shown = computed(() =>
  rows.value.filter((r) => !status.value || r.status === status.value)
)

function linenName(id) {
  const hit = linens.value.find((l) => l.id === id)
  return hit ? `${hit.code} ${hit.name}` : `#${id}`
}

function batchCode(id) {
  const hit = batches.value.find((b) => b.id === id)
  return hit ? hit.code : `#${id}`
}

function rowClass({ row }) {
  return row.status === '未结案' ? 'need-do' : ''
}

async function load() {
  try {
    rows.value = await shortageApi.list({})
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function doReplenish(row) {
  try {
    await ElMessageBox.confirm(
      `确认短少的 ${row.shortQty} 件已经找回？确认后当场加回可领用在库，追差结案。`,
      `补回入库 · ${row.code}`,
      { confirmButtonText: '补回入库', cancelButtonText: '再想想', type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await shortageApi.replenish(row.id)
    ElMessage.success(`已补回 ${row.shortQty} 件入库，追差结案`)
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function openLoss(row) {
  lossRow.value = row
  lossReason.value = '丢失'
  lossVisible.value = true
}

async function submitLoss() {
  try {
    await shortageApi.toLoss(lossRow.value.id, lossReason.value)
    ElMessage.success('已转报损，等报损确认后追差才结案')
    lossVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(async () => {
  try {
    linens.value = await linenApi.list({})
    batches.value = await batchApi.list({})
  } catch (e) {
    ElMessage.error(e.message)
  }
  await load()
})
</script>

<style scoped>
.chips {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}
.chips span {
  font-size: 13px;
  padding: 5px 14px;
  border-radius: 15px;
  border: 1px solid #e4e7ed;
  color: #606266;
  cursor: pointer;
  user-select: none;
  background: #fff;
}
.chips span:hover {
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
}
.chips span.on {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
  font-weight: 600;
}
.chips .rule {
  border: none;
  background: transparent;
  cursor: default;
  font-size: 12px;
  color: #b88230;
}
.chips .rule:hover {
  border: none;
  color: #b88230;
}
.mono {
  font-family: monospace;
  color: #606266;
}
.short {
  color: #c45656;
  font-family: monospace;
}
.st {
  font-size: 12px;
  border-radius: 3px;
  padding: 1px 9px;
}
.s-open {
  background: #fef0f0;
  color: #c45656;
  font-weight: 600;
}
.s-done {
  background: #f0f9eb;
  color: #529b2e;
}
.lnk {
  color: var(--el-color-primary);
  cursor: pointer;
  font-size: 12px;
  margin-right: 14px;
}
.lnk:hover {
  text-decoration: underline;
}
.lnk.warn {
  color: #c45656;
}
.hint {
  font-size: 12px;
  color: #b88230;
}
.hint.done {
  color: #529b2e;
}
.dim {
  color: #a8abb2;
  font-size: 12px;
}
.foot {
  margin-top: 12px;
  font-size: 12px;
  color: #c0c4cc;
}
</style>
