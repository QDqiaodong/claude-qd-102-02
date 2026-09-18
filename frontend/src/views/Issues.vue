<template>
  <div>
    <div class="daybar">
      <span class="lbl">按天看</span>
      <span v-for="d in days" :key="d" class="day" :class="{ on: d === cur }" @click="cur = d">
        {{ d.slice(5) }}
      </span>
      <span class="grow" />
      <button class="main" @click="openCreate">登记送出</button>
    </div>

    <div class="sum">
      <div><b>{{ dayRows.length }}</b><span>条记录</span></div>
      <div><b>{{ sumOut }}</b><span>送出件数</span></div>
      <div><b>{{ sumBack }}</b><span>收回件数</span></div>
      <div :class="{ alarm: sumOut - sumBack > 0 }">
        <b>{{ sumOut - sumBack }}</b><span>还在楼层</span>
      </div>
    </div>

    <div class="list">
      <div v-for="i in dayRows" :key="i.id" class="row">
        <span class="floor">{{ i.floorCode }}</span>
        <span class="lin">{{ linenName(i.linenId) }}</span>
        <span class="qty">送 <b>{{ i.sendQty }}</b></span>
        <span v-if="i.backQty !== null && i.backQty !== undefined" class="qty back">
          回 <b>{{ i.backQty }}</b>
        </span>
        <span class="who">{{ i.receiver }}</span>
        <span class="grow" />
        <span v-if="i.status === '已送出'" class="lnk" @click="openBack(i)">登记收回</span>
        <span v-else class="done">已收回</span>
      </div>
      <div v-if="!dayRows.length" class="empty">这一天没有收发记录</div>
    </div>

    <el-dialog v-model="visible" title="登记送出" width="450px">
      <el-form label-width="96px">
        <el-form-item label="布草">
          <el-select v-model="form.linenId" style="width:100%">
            <el-option v-for="l in linens" :key="l.id" :label="`${l.code} ${l.name}`" :value="l.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="楼层">
          <el-input v-model="form.floorCode" placeholder="如 8F" />
        </el-form-item>
        <el-form-item label="送出日期">
          <el-date-picker v-model="form.issueDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="送出件数">
          <el-input-number v-model="form.sendQty" :min="1" />
        </el-form-item>
        <el-form-item label="接收人">
          <el-input v-model="form.receiver" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="save">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="backVisible" title="登记收回" width="400px">
      <div class="back-tip">
        {{ linenName(backRow.linenId) }} · {{ backRow.floorCode }} 这天送出了
        <b>{{ backRow.sendQty }}</b> 件
      </div>
      <el-form label-width="96px">
        <el-form-item label="收回件数">
          <el-input-number v-model="backQty" :min="1" :max="backRow.sendQty" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="backVisible = false">取消</el-button>
        <el-button type="primary" @click="doBack">确认收回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { issueApi, linenApi } from '../api'

const rows = ref([])
const linens = ref([])
const cur = ref('')
const visible = ref(false)
const form = ref({})
const backVisible = ref(false)
const backRow = ref({})
const backQty = ref(1)

const days = computed(() => [...new Set(rows.value.map((r) => r.issueDate))].sort().reverse())
const dayRows = computed(() => rows.value.filter((r) => r.issueDate === cur.value))
const sumOut = computed(() => dayRows.value.reduce((s, r) => s + (r.sendQty || 0), 0))
const sumBack = computed(() =>
  dayRows.value.reduce((s, r) => s + (r.backQty || 0), 0)
)

function linenName(id) {
  const hit = linens.value.find((l) => l.id === id)
  return hit ? `${hit.code} ${hit.name}` : id
}

async function load() {
  try {
    rows.value = await issueApi.list({})
    if (!cur.value || !days.value.includes(cur.value)) cur.value = days.value[0] || ''
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
  form.value = { issueDate: cur.value || new Date().toISOString().slice(0, 10) }
  visible.value = true
}

async function save() {
  try {
    await issueApi.create(form.value)
    ElMessage.success('已登记')
    visible.value = false
    await load()
    cur.value = form.value.issueDate
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function openBack(row) {
  backRow.value = row
  backQty.value = row.sendQty
  backVisible.value = true
}

async function doBack() {
  try {
    await issueApi.giveBack(backRow.value.id, backQty.value)
    ElMessage.success('已收回')
    backVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(async () => {
  await loadLinens()
  await load()
})
</script>

<style scoped>
.daybar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}
.lbl {
  font-size: 12px;
  color: #909399;
  margin-right: 4px;
}
.day {
  font-size: 13px;
  font-family: monospace;
  padding: 5px 13px;
  border-radius: 15px;
  border: 1px solid #e4e7ed;
  color: #606266;
  cursor: pointer;
  user-select: none;
  background: #fff;
}
.day:hover {
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
}
.day.on {
  background: var(--el-color-primary);
  border-color: var(--el-color-primary);
  color: #fff;
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
.sum {
  display: flex;
  gap: 40px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 14px 22px;
  margin-bottom: 16px;
}
.sum div {
  display: flex;
  flex-direction: column;
}
.sum b {
  font-size: 22px;
  font-family: monospace;
  line-height: 1.2;
  color: var(--el-color-primary);
}
.sum span {
  font-size: 12px;
  color: #909399;
}
.sum div.alarm b {
  color: #e6a23c;
}
.list {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  overflow: hidden;
}
.row {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 18px;
  border-bottom: 1px solid #f5f7fa;
  font-size: 13px;
}
.row:last-child {
  border-bottom: none;
}
.row:hover {
  background: #fafcff;
}
.floor {
  font-family: monospace;
  font-weight: 600;
  color: #303133;
  background: #f4f4f5;
  border-radius: 3px;
  padding: 2px 9px;
}
.lin {
  min-width: 190px;
}
.qty {
  color: #909399;
}
.qty b {
  color: #303133;
  font-family: monospace;
}
.qty.back b {
  color: #67c23a;
}
.who {
  color: #a8abb2;
  font-size: 12px;
}
.lnk {
  color: var(--el-color-primary);
  cursor: pointer;
  font-size: 12px;
}
.lnk:hover {
  text-decoration: underline;
}
.done {
  color: #67c23a;
  font-size: 12px;
}
.empty {
  text-align: center;
  color: #c0c4cc;
  padding: 46px 0;
  font-size: 13px;
}
.back-tip {
  font-size: 13px;
  color: #606266;
  margin-bottom: 14px;
}
.back-tip b {
  color: var(--el-color-primary);
}
</style>
