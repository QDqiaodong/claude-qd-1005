<script setup>
import { computed, inject, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '../api'

const refreshHealth = inject('refreshHealth', () => {})
const route = useRoute()
const router = useRouter()

const rows = ref([])
const segments = ref([])
const patrolPoints = ref([])
const facilities = ref([])
const error = ref('')
const tip = ref('')
const loading = ref(false)
const segmentFilter = ref('')
const statusFilter = ref('')

const severities = ['一般', '重要', '紧急']
const statuses = ['待处置', '处理中', '已解除']
const sourceTypes = [
  { value: 'PATROL', label: '巡检点' },
  { value: 'FACILITY', label: '附属设备' }
]

// 「yyyy-MM-dd HH:mm」展示：后端返回 ISO 本地时间（无时区后缀），只做字符替换
function fmt(t) {
  return t ? String(t).replace('T', ' ').slice(0, 16) : ''
}
function sourceTypeLabel(t) {
  return sourceTypes.find(x => x.value === t)?.label || t
}
function nowLocal() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

const stats = computed(() => ({
  total: rows.value.length,
  open: rows.value.filter(r => r.status === '待处置').length,
  processing: rows.value.filter(r => r.status === '处理中').length,
  resolved: rows.value.filter(r => r.status === '已解除').length
}))

async function load() {
  loading.value = true; error.value = ''
  try {
    rows.value = await api.listRiskEvents(segmentFilter.value || '', statusFilter.value || '')
  } catch (e) { error.value = e.message } finally { loading.value = false }
}

async function loadObjects(segmentId) {
  patrolPoints.value = []
  facilities.value = []
  if (!segmentId) return
  try {
    const [pts, facs] = await Promise.all([
      api.listPatrolPoints(segmentId),
      api.listFacilities(segmentId)
    ])
    patrolPoints.value = pts || []
    facilities.value = facs || []
  } catch (e) { error.value = e.message }
}

// ---------------- 登记风险 ----------------
const createDialog = ref(false)
const saving = ref(false)
const form = ref(blank())
const prefillTip = ref('')
function blank() {
  return {
    segmentId: '', sourceType: 'PATROL', sourceId: '',
    severity: '一般', description: '', foundAt: nowLocal()
  }
}

const objectOptions = computed(() =>
  form.value.sourceType === 'PATROL' ? patrolPoints.value : facilities.value
)

// 程序赋值（如从巡检点/设备页带入）不能清空已选对象：这里只负责加载对象列表；
// 清空交给用户操作触发的 @change（onSegmentChange / 类型切换）。
watch(() => form.value.segmentId, (sid) => { loadObjects(sid) })
function onSegmentChange() { form.value.sourceId = '' }
function onTypeChange() { form.value.sourceId = '' }

function openCreate(prefill) {
  form.value = blank()
  prefillTip.value = ''
  error.value = ''; tip.value = ''
  if (prefill && prefill.sourceType && prefill.sourceId) {
    form.value.segmentId = Number(prefill.segmentId) || ''
    form.value.sourceType = prefill.sourceType
    form.value.sourceId = Number(prefill.sourceId) || ''
    prefillTip.value = `已从${sourceTypeLabel(prefill.sourceType)}带入发现对象，请补全风险信息`
  } else if (segments.value.length) {
    form.value.segmentId = segments.value[0].id
  }
  createDialog.value = true
}

async function save() {
  error.value = ''
  if (!form.value.segmentId) { error.value = '必须关联舱段'; return }
  if (!form.value.sourceId) { error.value = '必须选择发现对象（巡检点或附属设备）'; return }
  if (!form.value.description.trim()) { error.value = '风险描述不能为空'; return }
  if (!form.value.foundAt) { error.value = '必须填写发现时间'; return }
  saving.value = true
  try {
    const d = {
      segmentId: Number(form.value.segmentId),
      sourceType: form.value.sourceType,
      sourceId: Number(form.value.sourceId),
      severity: form.value.severity,
      description: form.value.description.trim(),
      foundAt: form.value.foundAt
    }
    await api.createRiskEvent(d)
    tip.value = '风险事件已登记，舱段已封控（须解除后才能恢复运营）'
    createDialog.value = false
    await Promise.all([load(), refreshHealth()])
  } catch (e) { error.value = e.message } finally { saving.value = false }
}

// ---------------- 推进 / 解除 ----------------
const actionDialog = ref(false)
const actionSaving = ref(false)
const actionMode = ref('advance') // advance | resolve
const actionRow = ref(null)
const actionForm = ref({ handleNote: '', resolver: '' })

function openAdvance(row) {
  actionMode.value = 'advance'
  actionRow.value = row
  actionForm.value = { handleNote: '', resolver: '' }
  error.value = ''
  actionDialog.value = true
}
function openResolve(row) {
  actionMode.value = 'resolve'
  actionRow.value = row
  actionForm.value = { handleNote: '', resolver: '' }
  error.value = ''
  actionDialog.value = true
}
async function submitAction() {
  error.value = ''
  if (actionMode.value === 'resolve') {
    if (!actionForm.value.handleNote.trim()) { error.value = '解除风险必须填写处置说明'; return }
    if (!actionForm.value.resolver.trim()) { error.value = '必须填写解除人'; return }
  }
  actionSaving.value = true
  try {
    if (actionMode.value === 'advance') {
      await api.advanceRiskEvent(actionRow.value.id, actionForm.value.handleNote.trim())
      tip.value = `事件 #${actionRow.value.id} 已推进为「处理中」`
    } else {
      await api.resolveRiskEvent(actionRow.value.id, {
        handleNote: actionForm.value.handleNote.trim(),
        resolver: actionForm.value.resolver.trim()
      })
      tip.value = `事件 #${actionRow.value.id} 已解除；如该舱段已无未解除事件，可在舱段页恢复运营`
    }
    actionDialog.value = false
    await Promise.all([load(), refreshHealth()])
  } catch (e) { error.value = e.message } finally { actionSaving.value = false }
}

// ---------------- 详情 ----------------
const detail = ref(null)
const detailOpen = ref(false)
async function openDetail(row) {
  error.value = ''
  try {
    detail.value = await api.getRiskEvent(row.id)
    detailOpen.value = true
  } catch (e) { error.value = e.message }
}

onMounted(async () => {
  try { segments.value = await api.listSegments() } catch (e) {}
  await load()
  // 从巡检点 / 附属设备页面「登记风险」跳入：带 query 时自动打开登记表单
  const q = route.query
  if (q.sourceType && q.sourceId && q.segmentId) {
    openCreate({ segmentId: q.segmentId, sourceType: q.sourceType, sourceId: q.sourceId })
    router.replace({ path: '/risk' })
  }
})
</script>

<template>
  <div>
    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">巡检异常 / 风险事件</span>
        <span class="panel-desc">待处置 → 处理中 → 已解除；风险未全部解除时舱段不能从维护/停用恢复运营</span>
      </div>

      <div v-if="error" class="err">{{ error }}</div>
      <div v-if="tip" class="ok-tip">{{ tip }}</div>

      <div class="stat-row">
        <div class="stat"><div class="k">事件总数</div><div class="v">{{ stats.total }}</div></div>
        <div class="stat"><div class="k">待处置</div><div class="v">{{ stats.open }}</div></div>
        <div class="stat"><div class="k">处理中</div><div class="v">{{ stats.processing }}</div></div>
        <div class="stat"><div class="k">已解除</div><div class="v">{{ stats.resolved }}</div></div>
      </div>

      <div class="toolbar">
        <el-select v-model="segmentFilter" placeholder="全部舱段" clearable style="width: 160px" @change="load">
          <el-option v-for="s in segments" :key="s.id" :label="s.segCode" :value="s.id" />
        </el-select>
        <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width: 140px" @change="load">
          <el-option v-for="s in statuses" :key="s" :label="s" :value="s" />
        </el-select>
        <span class="spacer"></span>
        <el-button type="primary" @click="openCreate()">登记风险事件</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column label="舱段" width="120">
          <template #default="{ row }">
            <div style="font-weight:700">{{ row.segCode }}</div>
            <div class="cell-sub">{{ row.segName }}</div>
          </template>
        </el-table-column>
        <el-table-column label="发现对象" min-width="180">
          <template #default="{ row }">
            <el-tag size="small" :type="row.sourceType === 'PATROL' ? 'warning' : 'info'">
              {{ sourceTypeLabel(row.sourceType) }}
            </el-tag>
            <span style="margin-left:6px;font-weight:600">{{ row.sourceName }}</span>
            <div class="cell-sub">{{ row.sourceDetail }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="severity" label="严重等级" width="95">
          <template #default="{ row }">
            <el-tag :type="row.severity === '紧急' ? 'danger' : row.severity === '重要' ? 'warning' : 'info'">
              {{ row.severity }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="95">
          <template #default="{ row }">
            <el-tag :type="row.status === '已解除' ? 'success' : row.status === '处理中' ? 'warning' : 'danger'">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="当前封控原因 / 描述" min-width="260">
          <template #default="{ row }">
            <div>{{ row.description }}</div>
            <div class="cell-sub">
              <template v-if="row.status !== '已解除'">
                🔒 当前封控原因；该舱段未解除事件 {{ row.openCount }} 条
              </template>
              <template v-else>该事件已解除，不再作为封控原因</template>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="发现时间" width="155">
          <template #default="{ row }">{{ fmt(row.foundAt) }}</template>
        </el-table-column>
        <el-table-column label="处置 / 解除" min-width="170">
          <template #default="{ row }">
            <div v-if="row.status === '已解除'">
              <div class="cell-sub">解除人：{{ row.resolver }}</div>
              <div class="cell-sub">{{ fmt(row.resolvedAt) }}</div>
            </div>
            <div v-else-if="row.handleNote" class="cell-sub">处置：{{ row.handleNote }}</div>
            <span v-else class="cell-sub">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openDetail(row)">详情</el-button>
            <el-button
              size="small" type="primary" plain
              :disabled="row.status !== '待处置'"
              @click="openAdvance(row)"
            >处理中</el-button>
            <el-button
              size="small" type="success" plain
              :disabled="row.status !== '处理中'"
              @click="openResolve(row)"
            >解除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 登记风险 -->
    <el-dialog v-model="createDialog" title="登记巡检异常 / 风险事件" width="600px">
      <div v-if="prefillTip" class="ok-tip">{{ prefillTip }}</div>
      <el-form :model="form" label-width="110px" class="dialog-form">
        <el-form-item label="所属舱段">
          <el-select v-model="form.segmentId" style="width: 100%" placeholder="选择舱段" @change="onSegmentChange">
            <el-option v-for="s in segments" :key="s.id" :label="`${s.segCode} ${s.segName || ''}`" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="发现对象">
          <div style="display:flex;gap:8px;width:100%">
            <el-select v-model="form.sourceType" style="width: 120px" @change="onTypeChange">
              <el-option v-for="t in sourceTypes" :key="t.value" :label="t.label" :value="t.value" />
            </el-select>
            <el-select
              v-model="form.sourceId" style="flex:1"
              :placeholder="objectOptions.length ? '选择具体对象' : '该舱段下暂无对象'"
            >
              <el-option
                v-for="o in objectOptions" :key="o.id" :value="o.id"
                :label="form.sourceType === 'PATROL'
                  ? `${o.pointCode}（${o.location || '无位置'}）`
                  : `${o.facilityType}#${o.id}（${o.status}）`"
              />
            </el-select>
          </div>
        </el-form-item>
        <el-form-item label="严重等级">
          <el-radio-group v-model="form.severity">
            <el-radio v-for="x in severities" :key="x" :value="x">{{ x }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="发现时间">
          <el-date-picker
            v-model="form.foundAt" type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="选择发现时间" style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="风险描述" class="full">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="500" show-word-limit
                    placeholder="描述异常 / 风险现象、影响范围等" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">登记（初始状态：待处置）</el-button>
      </template>
    </el-dialog>

    <!-- 推进 / 解除 -->
    <el-dialog
      :model-value="actionDialog"
      :title="actionMode === 'advance' ? '推进为「处理中」' : '解除风险事件'"
      width="520px"
      @update:model-value="actionDialog = $event"
    >
      <el-form :model="actionForm" label-width="100px">
        <el-form-item label="处置说明">
          <el-input
            v-model="actionForm.handleNote" type="textarea" :rows="3" maxlength="500" show-word-limit
            :placeholder="actionMode === 'advance' ? '可填写当前处置安排（选填）' : '必须填写处置说明、整改结果'"
          />
        </el-form-item>
        <el-form-item v-if="actionMode === 'resolve'" label="解除人">
          <el-input v-model="actionForm.resolver" maxlength="32" placeholder="值守 / 处置负责人姓名" />
        </el-form-item>
        <div v-if="actionMode === 'resolve'" class="panel-desc">
          解除后将记录解除人与解除时间；同一舱段仍有其他未解除事件时，舱段依旧不能恢复运营。
        </div>
      </el-form>
      <template #footer>
        <el-button @click="actionDialog = false">取消</el-button>
        <el-button v-if="actionMode === 'advance'" type="primary" :loading="actionSaving" @click="submitAction">
          推进为处理中
        </el-button>
        <el-button v-else type="success" :loading="actionSaving" @click="submitAction">
          确认解除
        </el-button>
      </template>
    </el-dialog>

    <!-- 详情 -->
    <el-dialog v-model="detailOpen" title="风险事件详情" width="640px">
      <div v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="事件编号">#{{ detail.id }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="detail.status === '已解除' ? 'success' : detail.status === '处理中' ? 'warning' : 'danger'">
              {{ detail.status }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="所属舱段">{{ detail.segCode }} {{ detail.segName }}</el-descriptions-item>
          <el-descriptions-item label="舱段状态">
            <el-tag :type="detail.segStatus === '运营' ? 'success' : detail.segStatus === '维护' ? 'warning' : 'info'">
              {{ detail.segStatus }}
            </el-tag>
            <span v-if="detail.openCount > 0" class="cell-sub" style="margin-left:8px">
              未解除 {{ detail.openCount }} 条，封控中
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="发现对象">
            <el-tag size="small" :type="detail.sourceType === 'PATROL' ? 'warning' : 'info'">
              {{ sourceTypeLabel(detail.sourceType) }}
            </el-tag>
            {{ detail.sourceName }}
          </el-descriptions-item>
          <el-descriptions-item label="严重等级">{{ detail.severity }}</el-descriptions-item>
          <el-descriptions-item label="发现时间">{{ fmt(detail.foundAt) }}</el-descriptions-item>
          <el-descriptions-item label="登记时间">{{ fmt(detail.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="风险描述" :span="2">{{ detail.description }}</el-descriptions-item>
          <el-descriptions-item label="对象信息" :span="2">{{ detail.sourceDetail || '—' }}</el-descriptions-item>
          <el-descriptions-item label="处置说明" :span="2">{{ detail.handleNote || '—' }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.status === '已解除'" label="解除人">{{ detail.resolver }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.status === '已解除'" label="解除时间">{{ fmt(detail.resolvedAt) }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </el-dialog>
  </div>
</template>
