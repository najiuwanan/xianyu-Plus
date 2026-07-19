<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { deleteProductMaterial, listProductMaterials, type ProductMaterial } from '@/api/product-material'
import { toast } from '@/utils/toast'

defineOptions({ name: 'ProductMaterialLibrary' })
const router = useRouter()
const materials = ref<ProductMaterial[]>([])
const keyword = ref('')
const loading = ref(false)

const load = async () => {
  loading.value = true
  try {
    const result = await listProductMaterials(keyword.value)
    materials.value = result.data || []
  } finally {
    loading.value = false
  }
}

const remove = async (material: ProductMaterial) => {
  if (!window.confirm(`确认删除素材“${material.materialName}”吗？已发布的商品不会受影响。`)) return
  await deleteProductMaterial(material.id)
  toast.success('素材已删除')
  await load()
}

const edit = (material: ProductMaterial) => router.push({ path: '/product-publish', query: { materialId: material.id } })
const batch = (material: ProductMaterial) => router.push({ path: '/product-publish', query: { materialId: material.id, batch: '1' } })

onMounted(load)
</script>

<template>
  <main class="material-page">
    <header>
      <div><h1>商品素材库</h1><p>商品内容和图片可被多个账号复用；类目、动态属性与地址会按账号重新预检。</p></div>
      <button class="primary" @click="router.push('/product-publish')">新建商品素材</button>
    </header>
    <section class="toolbar">
      <input v-model="keyword" placeholder="搜索素材名称或商品标题" @keyup.enter="load">
      <button @click="load">{{ loading ? '查询中…' : '查询' }}</button>
      <span>共 {{ materials.length }} 份素材</span>
    </section>
    <section v-if="materials.length" class="material-grid">
      <article v-for="material in materials" :key="material.id">
        <img v-if="material.images?.[0]" :src="material.images[0].url" alt="商品主图">
        <div v-else class="empty-image">暂无图片</div>
        <div class="content">
          <small>{{ material.materialName }}</small>
          <h2>{{ material.title }}</h2>
          <p>{{ material.description || '尚未填写商品描述' }}</p>
          <div class="meta"><strong>¥{{ Number(material.price || 0).toFixed(2) }}</strong><span>{{ material.images?.length || 0 }} 张图片 · 库存 {{ material.quantity }}</span></div>
          <div class="actions">
            <button @click="edit(material)">编辑</button>
            <button class="batch" @click="batch(material)">多账号发布</button>
            <button class="danger" @click="remove(material)">删除</button>
          </div>
        </div>
      </article>
    </section>
    <section v-else class="empty-state">
      <strong>还没有商品素材</strong><p>先准备标题、描述、图片和价格，保存后即可多账号复用。</p>
      <button class="primary" @click="router.push('/product-publish')">创建第一份素材</button>
    </section>
  </main>
</template>

<style scoped>
.material-page{max-width:1280px;margin:0 auto;padding:4px 0 48px;color:#1d2939}.material-page header{display:flex;align-items:center;justify-content:space-between;gap:16px;margin-bottom:18px}.material-page h1{margin:0;font-size:27px}.material-page header p{margin:6px 0 0;color:#667085}.primary,.toolbar button,.actions button{border:1px solid #b2ccff;border-radius:9px;background:#fff;padding:9px 14px;color:#175cd3;font-weight:700;cursor:pointer}.primary{border-color:#1570ef;background:#1570ef;color:#fff}.toolbar{display:flex;align-items:center;gap:10px;margin-bottom:16px;padding:14px;border:1px solid #e4e7ec;border-radius:12px;background:#fff}.toolbar input{min-width:280px;border:1px solid #d0d5dd;border-radius:8px;padding:10px}.toolbar span{margin-left:auto;color:#667085;font-size:13px}.material-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:15px}.material-grid article{overflow:hidden;border:1px solid #e4e7ec;border-radius:14px;background:#fff;box-shadow:0 2px 8px #1018280a}.material-grid article>img,.empty-image{width:100%;height:190px;object-fit:cover}.empty-image{display:grid;place-items:center;background:#f2f4f7;color:#98a2b3}.content{padding:15px}.content small{color:#175cd3;font-weight:700}.content h2{overflow:hidden;margin:6px 0;font-size:17px;white-space:nowrap;text-overflow:ellipsis}.content p{display:-webkit-box;overflow:hidden;height:42px;margin:8px 0;color:#667085;font-size:13px;-webkit-box-orient:vertical;-webkit-line-clamp:2}.meta{display:flex;align-items:center;justify-content:space-between;margin:14px 0}.meta strong{color:#d92d20}.meta span{color:#667085;font-size:12px}.actions{display:flex;gap:7px}.actions button{padding:7px 10px}.actions .batch{border-color:#84adff;background:#eff4ff}.actions .danger{margin-left:auto;border-color:#fecdca;color:#b42318}.empty-state{padding:90px 20px;border:1px dashed #d0d5dd;border-radius:14px;background:#fff;text-align:center}.empty-state p{color:#667085}@media(max-width:1000px){.material-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:680px){.material-page header,.toolbar{align-items:stretch;flex-direction:column}.toolbar input{min-width:0}.toolbar span{margin-left:0}.material-grid{grid-template-columns:1fr}}
</style>
