<script setup>
import { computed, inject, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import api from '../api'

const refreshHealth = inject('refreshHealth', () => {})
const router = useRouter()

const rows = ref([])
const segments = ref([])
const segMap = computed(() => {
  const m = {}
  segments.value.forEach(s => { m[s.id] = s.segCode })
  return m
})
const error = ref('')
const tip = ref('')
const loading = ref(false)
const segmentFilter = ref('')

const facilityTypes = ['风机', '排水泵', '照明', '监控']
const statuses = ['正常', '故障']

const dialog = ref(false)
const saving = ref(false)
const form = ref(blank())
function blank() {
  return { segmentId: '', facilityType: '风机', status: '正常' }
}

async function load() {
  loading.value = true; error.value = ''
  try {
    const data = await api.listFacilities(segmentFilter.value || '')
    rows.value = data || []
  } catch (e) { error.value = e.message } finally { loading.value = false }
}

function openCreate() {
  form.value = blank()
  if (segments.value.length) form.value.segmentId = segments.value[0].id
  dialog.value = true; tip.value = ''; error.value = ''
}
async function save() {
  saving.value = true; error.value = ''
  try {
    await api.createFacility(form.value)
    tip.value = `设备（${form.value.facilityType}）已建档`
    dialog.value = false
    await Promise.all([load(), refreshHealth()])
  } catch (e) { error.value = e.message } finally { saving.value = false }
}

async function fault(row) {
  error.value = ''; tip.value = ''
  try {
    await api.faultFacility(row.id)
    tip.value = `${row.facilityType} 已报故障`
    await Promise.all([load(), refreshHealth()])
  } catch (e) { error.value = e.message }
}
async function restore(row) {
  error.value = ''; tip.value = ''
  try {
    await api.restoreFacility(row.id)
    tip.value = `${row.facilityType} 已恢复`
    await Promise.all([load(), refreshHealth()])
  } catch (e) { error.value = e.message }
}

/** 从附属设备登记风险事件：跳到风险页并带入舱段与设备 */
function registerRisk(row) {
  router.push({
    path: '/risk',
    query: { sourceType: 'FACILITY', sourceId: row.id, segmentId: row.segmentId }
  })
}

onMounted(async () => {
  try { segments.value = await api.listSegments() } catch (e) {}
  await load()
})
</script>

<template>
  <div>
    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">附属设备</span>
        <span class="panel-desc">设备报故障置为「故障」、修复恢复「正常」；故障可直接「登记风险」发起封控闭环</span>
      </div>

      <div v-if="error" class="err">{{ error }}</div>
      <div v-if="tip" class="ok-tip">{{ tip }}</div>

      <div class="stat-row">
        <div class="stat"><div class="k">设备数</div><div class="v">{{ rows.length }}</div></div>
        <div class="stat"><div class="k">正常</div><div class="v">{{ rows.filter(r => r.status === '正常').length }}</div></div>
        <div class="stat"><div class="k">故障</div><div class="v">{{ rows.filter(r => r.status === '故障').length }}</div></div>
      </div>

      <div class="toolbar">
        <el-select v-model="segmentFilter" placeholder="全部舱段" clearable style="width: 160px" @change="load">
          <el-option v-for="s in segments" :key="s.id" :label="s.segCode" :value="s.id" />
        </el-select>
        <span class="spacer"></span>
        <el-button type="primary" @click="openCreate">新建设备</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column label="所属舱段" width="130">
          <template #default="{ row }">{{ segMap[row.segmentId] || row.segmentId }}</template>
        </el-table-column>
        <el-table-column prop="facilityType" label="设备类型" width="120" />
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === '正常' ? 'success' : 'danger'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="290" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="danger" plain :disabled="row.status === '故障'" @click="fault(row)">报故障</el-button>
            <el-button size="small" :disabled="row.status === '正常'" @click="restore(row)">恢复</el-button>
            <el-button size="small" type="warning" plain @click="registerRisk(row)">登记风险</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialog" title="新建附属设备" width="560px">
      <el-form :model="form" label-width="110px" class="dialog-form">
        <el-form-item label="所属舱段">
          <el-select v-model="form.segmentId" style="width: 100%" placeholder="选择舱段">
            <el-option v-for="s in segments" :key="s.id" :label="s.segCode" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="设备类型">
          <el-select v-model="form.facilityType" style="width: 100%">
            <el-option v-for="t in facilityTypes" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" class="full">
          <el-radio-group v-model="form.status">
            <el-radio v-for="s in statuses" :key="s" :value="s">{{ s }}</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">建档</el-button>
      </template>
    </el-dialog>
  </div>
</template>
