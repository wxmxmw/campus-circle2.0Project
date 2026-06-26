<template>
  <view class="publish-page">
    <view class="nav-header">
      <text class="close-btn" @click="goBack">×</text>
      <text class="nav-title">发布帖子</text>
      <view class="placeholder"></view>
    </view>

    <view class="topic-section">
      <view class="topic-header" @click="toggleTopicPanel">
        <text class="topic-title">选择话题</text>
        <view class="topic-hint">
          <text>选一个合适的话题，会更容易被看到</text>
          <text class="arrow" :class="{ expanded: showTopicPanel }">⌄</text>
        </view>
      </view>

      <view v-show="showTopicPanel" class="topic-list">
        <view
          v-for="topic in topicList"
          :key="topic.id"
          class="topic-tag"
          :class="{ active: selectedTopic === topic.id }"
          @click="selectTopic(topic)"
        >
          <text v-if="topic.icon" class="topic-icon">{{ topic.icon }}</text>
          <text>{{ topic.name }}</text>
        </view>
      </view>

      <view v-if="selectedTopic && !showTopicPanel" class="selected-topic">
        <view class="topic-tag active">
          <text v-if="selectedTopicData.icon" class="topic-icon">{{ selectedTopicData.icon }}</text>
          <text>{{ selectedTopicData.name }}</text>
          <text class="remove-btn" @click.stop="clearTopic">×</text>
        </view>
      </view>
    </view>

    <view class="content-section">
      <input
        class="title-input"
        v-model="title"
        maxlength="60"
        placeholder="标题（可选，AI 可生成）"
      />

      <textarea
        class="content-input"
        v-model="content"
        placeholder="写点想说的内容..."
        :maxlength="2000"
        auto-height
      ></textarea>

      <view class="word-count">{{ content.length }}/2000</view>
    </view>

    <view class="ai-section">
      <view class="ai-header">
        <view>
          <text class="ai-title">AI 发帖助手</text>
          <text class="ai-subtitle">生成标题、润色正文、推荐话题和标签</text>
        </view>
        <button class="ai-btn" :disabled="aiLoading" @click="handleAiAssist">
          {{ aiLoading ? '生成中...' : '一键生成' }}
        </button>
      </view>

      <view v-if="aiSuggestion" class="ai-result">
        <view
          v-if="aiReview"
          class="ai-review-banner"
          :class="`risk-${aiReview.riskLevel || 'low'}`"
        >
          <text class="ai-review-title">AI审查：{{ reviewLabel }}</text>
          <text class="ai-review-msg">{{ aiReview.suggestion || '内容已完成审查，可继续编辑。' }}</text>
          <text v-if="reviewCategoriesText" class="ai-review-meta">风险类别：{{ reviewCategoriesText }}</text>
          <text v-if="reviewHitsText" class="ai-review-meta">命中规则：{{ reviewHitsText }}</text>
          <button
            v-if="(aiReview.riskLevel === 'medium' || aiReview.riskLevel === 'high') && aiReview.suggestion"
            class="small-btn"
            @click="applyReviewSuggestion"
          >按建议替换</button>
        </view>

        <view class="ai-result-item">
          <text class="ai-label">标题建议</text>
          <text class="ai-value">{{ aiSuggestion.title }}</text>
          <button class="small-btn" @click="useAiTitle">使用标题</button>
        </view>

        <view class="ai-result-item">
          <text class="ai-label">润色正文</text>
          <view class="ai-value ai-content-value">{{ aiSuggestion.polishedContent }}</view>
          <button class="small-btn" @click="useAiContent">使用正文</button>
        </view>

        <view class="ai-result-item">
          <text class="ai-label">推荐话题</text>
          <text class="ai-value">{{ aiSuggestion.recommendedCategoryName }}</text>
          <button class="small-btn" @click="useAiCategory">使用话题</button>
        </view>

        <view class="ai-result-item">
          <text class="ai-label">推荐标签</text>
          <view class="tag-list">
            <text
              v-for="tag in aiSuggestion.tags"
              :key="tag"
              class="ai-tag"
              :class="{ active: isSelectedTag(tag) }"
              @click="toggleTag(tag)"
            >{{ tag }}</text>
          </view>
          <button class="small-btn" @click="handleAiAssist">重新生成</button>
        </view>
      </view>
    </view>

    <view class="image-section">
      <view class="image-list">
        <view
          v-for="(img, index) in imageList"
          :key="index"
          class="image-item"
        >
          <image :src="img" mode="aspectFill" @click="previewImage(index)"></image>
          <view class="delete-btn" @click="deleteImage(index)">×</view>
        </view>

        <view
          v-if="imageList.length < 9"
          class="add-image-btn"
          @click="chooseImage"
        >
          <text class="add-icon">+</text>
        </view>
      </view>
      <text class="image-tip">最多上传 9 张图片</text>
    </view>

    <view v-if="selectedTopic === 1" class="product-section">
      <view class="section-title">商品信息</view>

      <view class="form-item">
        <text class="label">价格</text>
        <view class="price-input-wrapper">
          <text class="price-symbol">￥</text>
          <input
            class="price-input"
            type="digit"
            v-model="price"
            placeholder="输入价格"
          />
        </view>
      </view>

      <view class="form-item">
        <text class="label">交易方式</text>
        <view class="trade-options">
          <view class="trade-option" :class="{ active: tradeMethod === 'face' }" @click="tradeMethod = 'face'">
            <text>当面交易</text>
          </view>
          <view class="trade-option" :class="{ active: tradeMethod === 'delivery' }" @click="tradeMethod = 'delivery'">
            <text>邮寄</text>
          </view>
          <view class="trade-option" :class="{ active: tradeMethod === 'both' }" @click="tradeMethod = 'both'">
            <text>都可以</text>
          </view>
        </view>
      </view>
    </view>

    <view v-if="selectedTopic === 4" class="vote-section">
      <view class="section-title">投票选项</view>
      <view v-for="(option, index) in voteOptions" :key="index" class="vote-option-item">
        <input
          class="vote-input"
          v-model="voteOptions[index]"
          placeholder="请输入投票选项"
        />
        <text class="delete-option" @click="removeVoteOption(index)" v-if="voteOptions.length > 2">×</text>
      </view>
      <view class="add-option-btn" @click="addVoteOption" v-if="voteOptions.length < 6">
        <text>+ 添加选项</text>
      </view>
    </view>

    <view class="more-settings">
      <view class="setting-item">
        <text class="setting-label">匿名发布</text>
        <view class="switch" :class="{ active: isAnonymous }" @click="toggleAnonymous">
          <view class="switch-dot"></view>
        </view>
      </view>
    </view>

    <view class="bottom-bar">
      <view class="publish-btn" :class="{ disabled: !canPublish }" @click="handlePublish">
        发布
      </view>
    </view>
  </view>
</template>

<script>
import { publishPost } from '../../api/post.js';
import { getCategories } from '@/api/index.js';
import { assistPost } from '@/api/ai.js';
import { commonApi } from '@/api/common.js';

const extractUploadedImageUrl = (res) => {
  const data = res && res.data;
  if (typeof data === 'string') return data;
  if (data && typeof data.url === 'string') return data.url;
  if (data && Array.isArray(data.list) && data.list.length > 0 && data.list[0]?.url) return data.list[0].url;
  if (res && typeof res.url === 'string') return res.url;
  throw new Error('上传响应格式错误: ' + JSON.stringify(res));
};

export default {
  name: 'PublishPost',
  data() {
    return {
      showTopicPanel: true,
      selectedTopic: null,
      topicList: [],
      title: '',
      content: '',
      aiLoading: false,
      aiSuggestion: null,
      aiReview: null,
      selectedTags: [],
      imageList: [],
      price: '',
      tradeMethod: 'face',
      voteOptions: ['', ''],
      isAnonymous: false,
      publishing: false
    };
  },
  async mounted() {
    try {
      const res = await getCategories();
      if (res.code === 200) this.topicList = Array.isArray(res.data?.list) ? res.data.list : [];
    } catch (error) {
      console.error('获取话题列表失败:', error);
      uni.showToast({ title: '获取话题失败', icon: 'none' });
    }
  },
  computed: {
    selectedTopicData() {
      return this.topicList.find(t => t.id === this.selectedTopic) || {};
    },
    canPublish() {
      const hasContent = this.content.trim().length > 0 || this.imageList.length > 0;
      if (this.selectedTopic === 1 && !this.price) return false;
      if (this.selectedTopic === 4) {
        const validOptions = this.voteOptions.filter(o => o.trim());
        if (validOptions.length < 2) return false;
      }
      return hasContent && !this.publishing;
    },
    reviewLabel() {
      if (!this.aiReview?.riskLevel) return '低风险';
      if (this.aiReview.riskLevel === 'high') return '高风险';
      if (this.aiReview.riskLevel === 'medium') return '中风险';
      return '低风险';
    },
    reviewCategoriesText() {
      if (!Array.isArray(this.aiReview?.categories) || this.aiReview.categories.length === 0) return '';
      return this.aiReview.categories.join('、');
    },
    reviewHitsText() {
      if (!Array.isArray(this.aiReview?.hits) || this.aiReview.hits.length === 0) return '';
      return this.aiReview.hits.slice(0, 3).map(hit => hit.matchedText).join('、');
    }
  },
  methods: {
    goBack() { uni.navigateBack(); },
    toggleTopicPanel() { this.showTopicPanel = !this.showTopicPanel; },
    selectTopic(topic) { this.selectedTopic = topic.id; this.showTopicPanel = false; },
    clearTopic() { this.selectedTopic = null; this.showTopicPanel = true; },
    chooseImage() {
      const remainCount = 9 - this.imageList.length;
      uni.chooseImage({
        count: remainCount,
        sizeType: ['compressed'],
        sourceType: ['album', 'camera'],
        success: (res) => { this.imageList = [...this.imageList, ...res.tempFilePaths]; },
        fail: () => { uni.showToast({ title: '选择图片失败', icon: 'none' }); }
      });
    },
    previewImage(index) { uni.previewImage({ current: index, urls: this.imageList }); },
    deleteImage(index) { this.imageList.splice(index, 1); },
    addVoteOption() { if (this.voteOptions.length < 6) this.voteOptions.push(''); },
    removeVoteOption(index) { if (this.voteOptions.length > 2) this.voteOptions.splice(index, 1); },
    toggleAnonymous() { this.isAnonymous = !this.isAnonymous; },
    async handleAiAssist() {
      const draft = this.content.trim();
      if (!draft) return uni.showToast({ title: '请先输入正文', icon: 'none' });
      this.aiLoading = true;
      try {
        const res = await assistPost({ content: draft, categoryId: this.selectedTopic });
        if (res.code === 200) {
          this.aiSuggestion = res.data;
          this.aiReview = res.data?.review || null;
          this.selectedTags = [];
          if (this.aiReview?.riskLevel === 'high') {
            uni.showToast({ title: '已生成（检测到高风险提示）', icon: 'none' });
          } else if (this.aiReview?.riskLevel === 'medium') {
            uni.showToast({ title: '已生成（含风险提示）', icon: 'none' });
          } else {
            uni.showToast({ title: 'AI 建议已生成', icon: 'none' });
          }
        } else {
          uni.showToast({ title: res.message || 'AI 生成失败', icon: 'none' });
        }
      } catch (error) {
        uni.showToast({ title: error.message || 'AI 生成失败', icon: 'none' });
      } finally {
        this.aiLoading = false;
      }
    },
    useAiTitle() { if (this.aiSuggestion?.title) this.title = this.aiSuggestion.title; },
    useAiContent() { if (this.aiSuggestion?.polishedContent) this.content = this.aiSuggestion.polishedContent; },
    applyReviewSuggestion() {
      if (!this.aiReview?.suggestion) return;
      this.content = `${this.content}\n\n[AI审查建议]\n${this.aiReview.suggestion}`;
      uni.showToast({ title: '已附加审查建议', icon: 'none' });
    },
    useAiCategory() {
      if (!this.aiSuggestion?.recommendedCategoryId) return;
      const matched = this.topicList.find(item => item.id === this.aiSuggestion.recommendedCategoryId);
      if (matched) this.selectTopic(matched);
    },
    isSelectedTag(tag) { return this.selectedTags.includes(tag); },
    toggleTag(tag) {
      if (!tag) return;
      const index = this.selectedTags.indexOf(tag);
      if (index >= 0) return this.selectedTags.splice(index, 1);
      if (this.selectedTags.length >= 5) return uni.showToast({ title: '最多选择 5 个标签', icon: 'none' });
      this.selectedTags.push(tag);
    },
    async uploadImages(tempFiles) {
      const results = await Promise.all(tempFiles.map(file => commonApi.uploadBatchImages(file)));
      return results.map(extractUploadedImageUrl);
    },
    async handlePublish() {
      if (!this.canPublish || this.publishing) return;
      this.publishing = true;
      try {
        let uploadedImages = [];
        if (this.imageList.length > 0) uploadedImages = await this.uploadImages(this.imageList);
        const postData = {
          categoryId: this.selectedTopic,
          topicId: this.selectedTopic,
          topicName: this.selectedTopicData.name,
          title: this.title,
          content: this.content,
          tags: this.selectedTags,
          images: uploadedImages,
          isAnonymous: this.isAnonymous
        };
        if (this.selectedTopic === 1) {
          postData.product = { price: parseFloat(this.price) || 0, tradeMethod: this.tradeMethod };
        }
        if (this.selectedTopic === 4) {
          postData.vote = { options: this.voteOptions.filter(o => o.trim()) };
        }
        const res = await publishPost(postData);
        if (res.code === 200) {
          uni.showToast({ title: '发布成功', icon: 'success' });
          setTimeout(() => { uni.navigateBack(); }, 1500);
        } else {
          uni.showToast({ title: res.message || '发布失败', icon: 'none' });
        }
      } catch (error) {
        console.error('发布失败:', error);
        uni.showToast({ title: '发布失败，请重试', icon: 'none' });
      } finally {
        this.publishing = false;
      }
    }
  }
};
</script>

<style scoped>
.publish-page { min-height: 100vh; padding: 18rpx 20rpx 160rpx; }
.nav-header,.topic-section,.content-section,.ai-section,.image-section,.product-section,.vote-section,.more-settings {
  background: linear-gradient(145deg, rgba(255,255,255,.96), rgba(247,247,245,.94)), repeating-linear-gradient(125deg, rgba(17,17,17,.02) 0, rgba(17,17,17,.02) 2rpx, transparent 2rpx, transparent 22rpx);
  border: 1rpx solid rgba(17,17,17,.08); border-radius: 28rpx; box-shadow: 0 20rpx 56rpx rgba(17,17,17,.05);
}
.nav-header { display:flex; align-items:center; justify-content:space-between; padding:24rpx 28rpx; margin-bottom:18rpx; position:sticky; top:12rpx; backdrop-filter:blur(24rpx); -webkit-backdrop-filter:blur(24rpx); z-index:100; }
.close-btn { font-size:50rpx; color:#222220; padding:10rpx; line-height:1; }
.nav-title { font-size:34rpx; font-weight:bold; color:#171717; }
.placeholder { width:70rpx; }
.topic-section,.content-section,.ai-section,.image-section,.product-section,.vote-section,.more-settings { padding:28rpx; margin-bottom:18rpx; }
.topic-header,.ai-header,.setting-item,.form-item { display:flex; justify-content:space-between; align-items:center; }
.topic-title,.section-title,.setting-label,.ai-title { font-size:32rpx; font-weight:bold; color:#171717; }
.topic-hint,.ai-subtitle,.word-count,.image-tip { font-size:24rpx; color:#8d8d87; }
.topic-list,.tag-list,.image-list,.trade-options { display:flex; flex-wrap:wrap; gap:16rpx; }
.topic-list { margin-top:20rpx; }
.topic-tag,.trade-option,.ai-tag,.add-option-btn { display:flex; align-items:center; justify-content:center; padding:16rpx 24rpx; background-color:rgba(17,17,17,.04); border-radius:999rpx; font-size:26rpx; color:#666660; border:1rpx solid rgba(17,17,17,.08); }
.topic-tag.active,.trade-option.active,.ai-tag.active { background:repeating-linear-gradient(135deg, rgba(255,255,255,.14) 0, rgba(255,255,255,.14) 2rpx, transparent 2rpx, transparent 18rpx), #171717; color:#fff; border-color:#171717; }
.topic-icon { margin-right:6rpx; }
.selected-topic { margin-top:20rpx; }
.selected-topic .topic-tag { display:inline-flex; }
.remove-btn { margin-left:10rpx; font-size:28rpx; color:inherit; }
.title-input,.content-input,.price-input-wrapper,.vote-input { width:100%; background:rgba(17,17,17,.035); border:1rpx solid rgba(17,17,17,.08); border-radius:20rpx; color:#222220; }
.title-input { height:84rpx; padding:0 22rpx; margin-bottom:18rpx; font-size:30rpx; }
.content-input { min-height:240rpx; padding:22rpx; font-size:30rpx; line-height:1.7; }
.word-count { text-align:right; margin-top:16rpx; }
.ai-btn,.small-btn,.publish-btn { margin:0; border:none; color:#fff; background:repeating-linear-gradient(135deg, rgba(255,255,255,.14) 0, rgba(255,255,255,.14) 2rpx, transparent 2rpx, transparent 18rpx), linear-gradient(135deg, #1c1c1b 0%, #454540 100%); box-shadow:0 16rpx 32rpx rgba(17,17,17,.16); }
.ai-btn { min-width:180rpx; height:68rpx; border-radius:999rpx; font-size:24rpx; line-height:68rpx; }
.small-btn { align-self:flex-start; height:52rpx; padding:0 22rpx; border-radius:999rpx; font-size:22rpx; line-height:52rpx; }
.ai-btn[disabled] { background:rgba(17,17,17,.18); box-shadow:none; }
.ai-result { margin-top:22rpx; }
.ai-result-item { padding:20rpx 0; border-top:1rpx solid rgba(17,17,17,.08); }
.ai-review-banner { padding:18rpx; border-radius:16rpx; margin-bottom:14rpx; border:1rpx solid rgba(17,17,17,.08); background:rgba(17,17,17,.03); }
.ai-review-banner.risk-medium { background:rgba(245, 158, 11, .10); border-color:rgba(245, 158, 11, .55); }
.ai-review-banner.risk-high { background:rgba(239, 68, 68, .12); border-color:rgba(239, 68, 68, .55); }
.ai-review-title { display:block; font-size:25rpx; font-weight:bold; color:#222; }
.ai-review-msg { display:block; margin-top:8rpx; font-size:24rpx; color:#333; line-height:1.5; }
.ai-review-meta { display:block; margin-top:6rpx; font-size:22rpx; color:#595959; }
.ai-label { display:block; margin-bottom:10rpx; font-size:24rpx; color:#575753; font-weight:bold; }
.ai-value { display:block; margin-bottom:14rpx; font-size:26rpx; color:#252523; line-height:1.6; word-break:break-all; }
.ai-content-value { white-space:pre-wrap; word-break:break-word; }
.image-item { position:relative; width:200rpx; height:200rpx; }
.image-item image,.add-image-btn { width:100%; height:100%; border-radius:20rpx; border:1rpx solid rgba(17,17,17,.08); }
.image-item image { background:#ececea; }
.image-item .delete-btn { position:absolute; top:-15rpx; right:-15rpx; width:40rpx; height:40rpx; background-color:rgba(17,17,17,.8); color:#fff; border-radius:50%; display:flex; align-items:center; justify-content:center; font-size:28rpx; }
.add-image-btn { background:rgba(17,17,17,.03); display:flex; align-items:center; justify-content:center; }
.add-icon { font-size:60rpx; color:#8d8d87; }
.label { width:140rpx; font-size:28rpx; color:#666660; }
.price-input-wrapper { flex:1; display:flex; align-items:center; padding:0 20rpx; }
.price-symbol,.price-input { font-size:32rpx; color:#181817; }
.price-symbol { margin-right:10rpx; }
.price-input { flex:1; height:80rpx; background:transparent; }
.trade-options { flex:1; }
.vote-option-item { display:flex; align-items:center; margin-bottom:20rpx; }
.vote-input { flex:1; height:80rpx; padding:0 20rpx; font-size:28rpx; }
.delete-option { width:60rpx; text-align:center; font-size:36rpx; color:#8d8d87; }
.add-option-btn { margin-top:10rpx; }
.switch { width:100rpx; height:56rpx; background-color:rgba(17,17,17,.1); border-radius:28rpx; padding:6rpx; transition:background-color .3s; }
.switch.active { background-color:#171717; }
.switch-dot { width:44rpx; height:44rpx; background-color:#fff; border-radius:50%; transition:transform .3s; }
.switch.active .switch-dot { transform:translateX(44rpx); }
.bottom-bar { position:fixed; bottom:0; left:0; right:0; padding:18rpx 24rpx; padding-bottom:calc(18rpx + env(safe-area-inset-bottom)); background:rgba(243,243,241,.84); backdrop-filter:blur(22rpx); -webkit-backdrop-filter:blur(22rpx); border-top:1rpx solid rgba(17,17,17,.08); }
.publish-btn { height:92rpx; font-size:32rpx; font-weight:bold; border-radius:999rpx; display:flex; align-items:center; justify-content:center; }
.publish-btn.disabled { background:rgba(17,17,17,.18); pointer-events:none; box-shadow:none; }
</style>
