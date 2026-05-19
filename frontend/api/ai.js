import request from '@/utils/request'

export const assistPost = async (data) => {
  return await request.post('/ai/post-assist', {
    content: data.content,
    categoryId: data.categoryId || null
  })
}
