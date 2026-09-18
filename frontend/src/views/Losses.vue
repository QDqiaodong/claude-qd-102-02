<template>
  <div>
    <div class="chips">
      <span :class="{ on: reason === '' }" @click="reason = ''">全部</span>
      <span v-for="r in reasons" :key="r" :class="{ on: reason === r }" @click="reason = r">{{ r }}</span>
      <span class="grow" />
      <button class="main" @click="openCreate">登记报损</button>
    </div>

    <el-table :data="shown" :row-class-name="rowClass" style="width:100%">
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="detail">
            <div class="d-items">
              <div><label>布草</label><span>{{ linenName(row.linenId) }}</span></div>
              <div><label>报损日期</label><span class="mono">{{ row.lossDate }}</span></div>
              <div><label>件数</label><span class="mono">{{ row.quantity }}</span></div>
              <div><label>原因</label><span>{{ row.reason }}</span></div>
              <div><label>责任楼层</label><span>{{ row.dutyFloor || '未指定' }}</span></div>
              <div v-if="row.shortageId">
                <label>来源</label><span>追差 {{ shortageCode(row.shortageId) }} 转入</span>
              </div>
              <div>
                <label>状态</label>
                <span :class="row.status === '已确认' ? 'ok' : 'wait'">{{ row.status }}</span>
              </div>
            </div>
            <div class="d-act">
              <template v-if="row.status === '待确认'">
                <span v-if="row.shortageId" class="hint">
                  追差转入：短少件数收工时就没能回库，确认后追差结案，不再从在库扣第二遍
                </span>
                <span v-else class="hint">确认后会从在库件数里扣掉这 {{ row.quantity }} 件</span>
                <button class="danger" @click="confirm(row)">
                  {{ row.shortageId ? '确认并结追差' : '确认扣减' }}
                </button>
              </template>
              <template v-else>
                <span class="hint done">
                  {{ row.shortageId ? '已确认，来源追差同步结案' : '已于确认时扣减在库' }}
                </span>
              </template>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="布草" min-width="200">
        <template #default="{ row }">{{ linenName(row.linenId) }}</template>
      </el-table-column>
      <el-table-column prop="lossDate" label="报损日期" width="130" />
      <el-table-column prop="quantity" label="件数" width="90" />
      <el-table-column prop="reason" label="原因" width="110">
        <template #default="{ row }">
          <span class="pill" :class="'r-' + row.reason">{{ row.reason }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="dutyFloor" label="责任楼层" width="110">
        <template #default="{ row }">
          {{ row.dutyFloor || '—' }}
          <div v-if="row.shortageId" class="from-tag">追差 {{ shortageCode(row.shortageId) }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="110">
        <template #default="{ row }">
          <span class="st" :class="row.status === '已确认' ? 's-done' : 's-wait'">{{ row.status }}</span>
        </template>
      </el-table-column>
      <el-table-column label="" width="120">
        <template #default="{ row }">
          <span class="tip">{{ row.status === '待确认' ? '展开可确认 →' : '' }}</span>
        </template>
      </el-table-column>
    </el-table>

    <div class="foot">点每一行左边的箭头展开，里面是详情和确认按钮</div>

    <el-dialog v-model="visible" title="登记报损" width="450px">
      <el-form label-width="96px">
        <el-form-item label="布草">
          <el-select v-model="form.linenId" style="width:100%">
            <el-option v-for="l in linens" :key="l.id" :label="`${l.code} ${l.name}`" :value="l.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="报损日期">
          <el-date-picker v-model="form.lossDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="件数">
          <el-input-number v-model="form.quantity" :min="1" />
        </el-form-item>
        <el-form-item label="原因">
          <el-select v-model="form.reason" style="width:100%">
            <el-option label="破损" value="破损" />
            <el-option label="丢失" value="丢失" />
            <el-option label="污损" value="污损" />
          </el-select>
        </el-form-item>
        <el-form-item label="责任楼层">
          <el-input v-model="form.dutyFloor" placeholder="如 12F" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="save">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { lossApi, linenApi, shortageApi } from '../api'

const reasons = ['破损', '丢失', '污损']
const rows = ref([])
const linens = ref([])
const shortages = ref([])
const reason = ref('')
const visible = ref(false)
const form = ref({})

const shown = computed(() =>
  rows.value.filter((r) => !reason.value || r.reason === reason.value)
)

function linenName(id) {
  const hit = linens.value.find((l) => l.id === id)
  return hit ? `${hit.code} ${hit.name}` : id
}

function shortageCode(id) {
  const hit = shortages.value.find((s) => s.id === id)
  return hit ? hit.code : `#${id}`
}

function rowClass({ row }) {
  return row.status === '待确认' ? 'need-do' : ''
}

async function load() {
  try {
    rows.value = await lossApi.list({})
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
  form.value = { reason: '破损' }
  visible.value = true
}

async function save() {
  try {
    await lossApi.create(form.value)
    ElMessage.success('已登记')
    visible.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function confirm(row) {
  try {
    await lossApi.confirm(row.id)
    ElMessage.success(row.shortageId ? '已确认，追差同步结案' : '已确认并扣减在库')
    await load()
    await loadLinens()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(async () => {
  await loadLinens()
  shortages.value = await shortageApi.list({}).catch(() => [])
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
.chips span.grow {
  flex: 1;
  border: none;
  background: transparent;
  cursor: default;
}
.chips span.grow:hover {
  border: none;
}
.main {
  border: none;
  background: var(--el-color-primary);
  color: #fff;
  border-radius: 4px;
  padding: 8px 18px;
  font-size: 13px;
  cursor: pointer;
}
.detail {
  padding: 8px 40px 16px;
  background: #fafcff;
}
.d-items {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px 26px;
}
.d-items div {
  display: flex;
  gap: 10px;
  font-size: 13px;
}
.d-items label {
  color: #909399;
  min-width: 72px;
}
.mono {
  font-family: monospace;
}
.ok {
  color: #67c23a;
}
.wait {
  color: #e6a23c;
}
.d-act {
  margin-top: 16px;
  display: flex;
  align-items: center;
  gap: 16px;
}
.hint {
  font-size: 12px;
  color: #909399;
}
.hint.done {
  color: #67c23a;
}
.danger {
  border: none;
  background: #f56c6c;
  color: #fff;
  border-radius: 4px;
  padding: 7px 18px;
  font-size: 13px;
  cursor: pointer;
}
.danger:hover {
  filter: brightness(1.06);
}
.pill {
  font-size: 12px;
  border-radius: 3px;
  padding: 1px 9px;
  background: #f4f4f5;
  color: #606266;
}
.r-破损 {
  background: #fef0f0;
  color: #c45656;
}
.r-丢失 {
  background: #f4f4f5;
  color: #606266;
}
.r-污损 {
  background: #fdf6ec;
  color: #b88230;
}
.st {
  font-size: 12px;
  border-radius: 3px;
  padding: 1px 9px;
}
.from-tag {
  font-size: 11px;
  color: #b88230;
  font-family: monospace;
  margin-top: 2px;
}
.s-done {
  background: #f0f9eb;
  color: #529b2e;
}
.s-wait {
  background: #fdf6ec;
  color: #b88230;
  font-weight: 600;
}
.tip {
  font-size: 12px;
  color: #c0c4cc;
}
.foot {
  margin-top: 12px;
  font-size: 12px;
  color: #c0c4cc;
}
</style>
