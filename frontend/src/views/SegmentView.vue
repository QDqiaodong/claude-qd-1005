<script setup>
import { inject, onMounted, ref } from 'vue'
import api from '../api'

const refreshHealth = inject('refreshHealth', () => {})

const rows = ref([])
const error = ref('')
const tip = ref('')
const loading = ref(false)
const statusFilter = ref('')
const shapes = ['圆形', '矩形', '马蹄形']
const statuses = ['运营', '维护', '停用']

const dialog = ref(false)
const saving = ref(false)
const form = ref(blank())
function blank() {
  return { segCode: '', segName: '', shape: '矩形', lengthM: '', status: '运营' }
}

const detail = ref(null)
const detailOpen = ref(false)

async function load() {
  loading.value = true; error.value = ''
  try {
    const data = await api.listSegments(statusFilter.value || '')
    rows.value = data || []
  } catch (e) { error.value = e.message } finally { loading.value = false }
}

function openCreate() { form.value = blank(); dialog.value = true; tip.value = ''; error.value = '' }
async function save() {
  saving.value = true; error.value = ''
  try {
    const d = { ...form.value, lengthM: Number(form.value.lengthM) }
    await api.createSegment(d)
    tip.value = `舱段 ${d.segCode} 已建档`
    dialog.value = false
    await Promise.all([load(), refreshHealth()])
  } catch (e) { error.value = e.message } finally { saving.value = false }
}

async function openDetail(row) {
  error.value = ''
  try {
    detail.value = await api.getSegmentWithChildren(row.id)
    detailOpen.value = true
  } catch (e) { error.value = e.message }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">廊体舱段</span>
        <span class="panel-desc">新建舱段长度须 &gt; 0；断面形状：圆形 / 矩形 / 马蹄形</span>
      </div>

      <div v-if="error" class="err">{{ error }}</div>
      <div v-if="tip" class="ok-tip">{{ tip }}</div>

      <div class="stat-row">
        <div class="stat"><div class="k">舱段数</div><div class="v">{{ rows.length }}</div></div>
        <div class="stat"><div class="k">运营</div><div class="v">{{ rows.filter(r => r.status === '运营').length }}</div></div>
        <div class="stat"><div class="k">维护</div><div class="v">{{ rows.filter(r => r.status === '维护').length }}</div></div>
        <div class="stat"><div class="k">停用</div><div class="v">{{ rows.filter(r => r.status === '停用').length }}</div></div>
      </div>

      <div class="toolbar">
        <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width: 140px" @change="load">
          <el-option v-for="s in statuses" :key="s" :label="s" :value="s" />
        </el-select>
        <span class="spacer"></span>
        <el-button type="primary" @click="openCreate">新建舱段</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="segCode" label="舱段编码" width="120" />
        <el-table-column prop="segName" label="名称" min-width="150" />
        <el-table-column prop="shape" label="断面" width="100" />
        <el-table-column prop="lengthM" label="长度(m)" width="110" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === '运营' ? 'success' : row.status === '维护' ? 'warning' : 'info'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openDetail(row)">查看子项</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialog" title="新建舱段" width="560px">
      <el-form :model="form" label-width="110px" class="dialog-form">
        <el-form-item label="舱段编码"><el-input v-model="form.segCode" placeholder="如 SEG-05" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="form.segName" /></el-form-item>
        <el-form-item label="断面形状">
          <el-select v-model="form.shape" style="width: 100%">
            <el-option v-for="s in shapes" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="长度(m)"><el-input v-model="form.lengthM" type="number" step="0.1" /></el-form-item>
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

    <el-dialog v-model="detailOpen" title="舱段子项（嵌套查询）" width="880px">
      <div v-if="detail">
        <div class="panel-desc" style="margin-bottom:8px">
          {{ detail.segCode }} · {{ detail.segName }} · {{ detail.shape }} · 长度 {{ detail.lengthM }}m · {{ detail.status }}
        </div>
        <div class="panel-title" style="margin:10px 0 4px">入廊管线（{{ detail.pipelines.length }}）</div>
        <el-table :data="detail.pipelines" border size="small">
          <el-table-column prop="lineType" label="类型" />
          <el-table-column prop="diameterMm" label="管径(mm)" />
          <el-table-column prop="pressureLevel" label="压力级制" />
          <el-table-column prop="status" label="状态" />
        </el-table>
        <div class="panel-title" style="margin:10px 0 4px">巡检点（{{ detail.patrolPoints.length }}）</div>
        <el-table :data="detail.patrolPoints" border size="small">
          <el-table-column prop="pointCode" label="点位" />
          <el-table-column prop="location" label="位置" />
          <el-table-column prop="lastPatrolAt" label="最近巡检" />
        </el-table>
        <div class="panel-title" style="margin:10px 0 4px">附属设备（{{ detail.facilities.length }}）</div>
        <el-table :data="detail.facilities" border size="small">
          <el-table-column prop="facilityType" label="类型" />
          <el-table-column prop="status" label="状态" />
        </el-table>
      </div>
    </el-dialog>
  </div>
</template>
