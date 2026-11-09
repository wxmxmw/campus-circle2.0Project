<template>
  <view class="post-detail-page">
    <view class="nav-header">
      <text class="back-btn" @click="goBack">‹</text>
      <text class="nav-title">Post Detail</text>
      <text class="nav-more" @click="showPostMenu">⋯</text>
    </view>

    <view v-if="loading" class="loading-tip">Loading...</view>

    <scroll-view v-else class="scroll-content" scroll-y>
      <view class="post-card">
        <view class="post-header">
          <view class="user-info" @click="goToUser">
            <image class="avatar" :src="getFullImageUrl(post.userAvatar)" mode="aspectFill"></image>
            <view class="user-meta">
              <view class="user-name-row">
                <text class="username">{{ post.userName }}</text>
              </view>
              <text class="post-time">{{ post.time }}</text>
            </view>
          </view>
          <text class="more-btn" @click="showPostMenu">⋯</text>
        </view>

        <view class="post-content">
          <text v-if="post.title" class="post-title">{{ post.title }}</text>
          <view class="post-meta-tags">
            <text v-if="post.tag" class="category-chip">{{ post.tag }}</text>
            <text v-for="tag in (post.tags || [])" :key="tag" class="tag-chip">#{{ tag }}</text>
          </view>
          <text class="content-text">{{ post.content }}</text>
        </view>

        <view v-if="post.images && post.images.length > 0" class="post-images">
          <image
            v-for="(img, index) in post.images"
            :key="index"
            class="post-image"
            :src="getFullImageUrl(img)"
            mode="aspectFill"
            @click="previewImage(index)"
          ></image>
        </view>

        <view v-if="post.vote && post.vote.options && post.vote.options.length" class="vote-card">
          <view class="vote-header">
            <text class="vote-title">Vote</text>
            <text class="vote-subtitle">{{ post.vote.totalVotes || 0 }} joined</text>
          </view>
          <view class="vote-options">
            <view
              v-for="opt in post.vote.options"
              :key="opt.id"
              class="vote-option"
              :class="{ selected: post.vote.myOptionId === opt.id }"
              @click="handleVote(opt)"
            >
              <view class="vote-option-row">
                <text class="vote-option-text">{{ opt.text }}</text>
                <text class="vote-option-count">{{ opt.count || 0 }} votes</text>
              </view>
              <view class="vote-bar">
                <view class="vote-bar-fill" :style="{ width: (opt.percent || 0) + '%' }"></view>
              </view>
            </view>
          </view>
          <text v-if="post.vote.myOptionId" class="vote-hint">Voted</text>
          <text v-else class="vote-hint">Tap to vote</text>
        </view>

        <view class="action-bar">
          <view class="action-item" @click="handleCollect">
            <text class="action-icon" :class="{ active: isCollected }">{{ isCollected ? '★' : '☆' }}</text>
            <text class="action-text">{{ post.collectCount > 0 ? post.collectCount : 'Collect' }}</text>
          </view>
          <view class="action-item" @click="handleLike">
            <text class="action-icon heart-icon" :class="{ active: isLiked }">{{ isLiked ? '♥' : '♡' }}</text>
            <text class="action-text">{{ post.likes > 0 ? post.likes : 'Like' }}</text>
          </view>
        </view>
      </view>

      <view class="comment-section">
        <view class="comment-header">
          <text class="comment-title">Comments · {{ comments.length }}</text>
          <view class="sort-btn"><text>Default</text></view>
        </view>

        <view class="comment-list">
          <view v-for="comment in comments" :key="comment.id" class="comment-item">
            <view class="comment-main">
              <image class="comment-avatar" :src="getFullImageUrl(comment.avatar)" mode="aspectFill" @click="goToUserProfile(comment.userId)"></image>
              <view class="comment-body">
                <view class="comment-user">
                  <text class="comment-username">{{ comment.username }}</text>
                  <text v-if="comment.isAuthor" class="author-tag">Author</text>
                </view>
                <text class="comment-content" @click="replyToComment(comment)">{{ comment.content }}</text>
                <view class="comment-footer">
                  <text class="comment-time">{{ comment.time }}</text>
                  <view class="comment-actions">
                    <view class="comment-action" @click="handleLikeComment(comment)">
                      <text class="comment-like-icon" :class="{ liked: comment.isLiked }">{{ comment.isLiked ? '♥' : '♡' }}</text>
                      <text class="comment-like-text">{{ comment.likes > 0 ? comment.likes : 'Like' }}</text>
                    </view>
                    <text class="comment-more" @click="showCommentMenu(comment)">⋯</text>
                  </view>
                </view>

                <view v-if="comment.replies && comment.replies.length > 0" class="reply-list">
                  <view v-for="reply in comment.replies" :key="reply.id" class="reply-item">
                    <image class="reply-avatar" :src="getFullImageUrl(reply.avatar)" mode="aspectFill" @click="goToUserProfile(reply.userId)"></image>
                    <view class="reply-body">
                      <view class="reply-user">
                        <text class="reply-username">{{ reply.username }}</text>
                        <text v-if="reply.isAuthor" class="author-tag">Author</text>
                        <text v-if="reply.isOP" class="op-tag">OP</text>
                        <text class="reply-time">{{ reply.time }}</text>
                        <text class="reply-like" @click="handleLikeReply(reply)">
                          <text class="reply-like-icon" :class="{ liked: reply.isLiked }">{{ reply.isLiked ? '♥' : '♡' }}</text>
                          <text v-if="reply.likes > 0">{{ reply.likes }}</text>
                        </text>
                        <text class="reply-more" @click="showReplyMenu(reply, comment)">⋯</text>
                      </view>
                      <text class="reply-content" @click="replyToReply(reply, comment)">
                        <text v-if="reply.replyTo" class="reply-to">@{{ reply.replyTo }} </text>
                        {{ reply.content }}
                      </text>
                    </view>
                  </view>
                </view>
              </view>
            </view>
          </view>

          <view v-if="comments.length === 0" class="empty-comment">No comments yet</view>
        </view>
      </view>

      <view class="bottom-space"></view>
    </scroll-view>

    <view class="comment-input-bar">
      <text class="home-icon" @click="goHome">⌂</text>
      <view class="input-wrapper">
        <input class="comment-input" v-model="commentText" :placeholder="replyPlaceholder" :adjust-position="true" @focus="handleInputFocus" @blur="handleInputBlur" />
        <text class="emoji-btn" @click="toggleEmojiPanel">☺</text>
      </view>
      <text class="image-btn">▣</text>
      <view class="send-btn" :class="{ active: commentText.trim() }" @click="handleSendComment">Send</view>
    </view>

    <view v-if="showEmojiPanel" class="emoji-panel">
      <scroll-view class="emoji-content" scroll-y>
        <view class="emoji-grid">
          <text v-for="(emoji, index) in emojiList" :key="index" class="emoji-item" @click="insertEmoji(emoji)">{{ emoji }}</text>
        </view>
      </scroll-view>
    </view>

    <view v-if="replyTarget" class="reply-bar">
      <text class="reply-hint">Reply @{{ replyTarget.username }}</text>
      <text class="cancel-reply" @click="cancelReply">Cancel</text>
    </view>
  </view>
</template>

<script>
import { getPostDetail, getPostComments, likePost, collectPost, addComment, likeComment, reportPost, reportComment, votePost } from '../../api/post.js';
import { getBackendOrigin } from '@/utils/api'

const BASE_URL = getBackendOrigin();

export default {
  data() {
    return {
      postId: null,
      loading: true,
      isLiked: false,
      isCollected: false,
      commentText: '',
      showEmojiPanel: false,
      emojiList: ['😀', '😄', '🥹', '😭', '😎', '🥰', '👍', '👏', '🔥', '🎉', '💯', '❤️'],
      replyTarget: null,
      post: {},
      comments: []
    };
  },
  computed: {
    replyPlaceholder() {
      return this.replyTarget ? `Reply @${this.replyTarget.username}` : 'Say something';
    }
  },
  onLoad(options) {
    if (options.id) {
      this.postId = Number(options.id);
      this.initData();
    }
  },
  methods: {
    getFullImageUrl(url) {
      if (!url) return '/static/logo.png';
      if (url.startsWith('http')) return url;
      return BASE_URL + (url.startsWith('/') ? url : '/' + url);
    },
    async initData() {
      this.loading = true;
      try {
        await Promise.all([this.fetchPostDetail(), this.fetchComments()]);
      } catch (error) {
        console.error('Loading failed:', error);
        uni.showToast({ title: 'Load failed', icon: 'none' });
      } finally {
        this.loading = false;
      }
    },
    async fetchPostDetail() {
      const res = await getPostDetail(this.postId);
      if (res.code === 200) {
        this.post = res.data;
        this.isLiked = !!res.data.isLiked;
        this.isCollected = !!res.data.isCollected;
      } else {
        uni.showToast({ title: res.message, icon: 'none' });
      }
    },
    async handleVote(option) {
      if (!option || !option.id) return;
      if (this.post.vote && this.post.vote.myOptionId) {
        uni.showToast({ title: 'Already voted', icon: 'none' });
        return;
      }
      try {
        uni.showLoading({ title: 'Voting...' });
        const res = await votePost(this.postId, option.id);
        if (res.code === 200) {
          this.post = { ...this.post, vote: res.data };
          uni.showToast({ title: 'Vote success', icon: 'none' });
        }
      } catch (e) {
        uni.showToast({ title: e.message || 'Vote failed', icon: 'none' });
      } finally {
        uni.hideLoading();
      }
    },
    async fetchComments() {
      const res = await getPostComments(this.postId);
      if (res.code === 200) this.comments = res.data.list;
    },
    showPostMenu() {
      uni.showActionSheet({
        itemList: ['Report', 'Copy link', 'Share'],
        success: (res) => {
          if (res.tapIndex === 0) this.reportPost();
          else if (res.tapIndex === 1) this.copyLink();
          else if (res.tapIndex === 2) this.sharePost();
        }
      });
    },
    reportPost() {
      uni.showActionSheet({
        itemList: ['Spam', 'Illegal', 'Abuse', 'Copyright', 'Attack', 'Other'],
        success: async (res) => {
          const reasons = ['Spam', 'Illegal', 'Abuse', 'Copyright', 'Attack', 'Other'];
          try {
            const result = await reportPost(this.postId, reasons[res.tapIndex]);
            if (result.code === 200) uni.showToast({ title: 'Reported', icon: 'none' });
          } catch (error) {
            uni.showToast({ title: 'Report failed', icon: 'none' });
          }
        }
      });
    },
    copyLink() {
      uni.setClipboardData({
        data: `https://example.com/post/${this.postId}`,
        success: () => uni.showToast({ title: 'Link copied', icon: 'none' })
      });
    },
    sharePost() {
      uni.showToast({ title: 'Share coming soon', icon: 'none' });
    },
    showCommentMenu(comment) {
      const items = comment.isMine ? ['Delete', 'Report'] : ['Reply', 'Report'];
      uni.showActionSheet({
        itemList: items,
        success: (res) => {
          const action = items[res.tapIndex];
          if (action === 'Delete') this.deleteComment(comment);
          else if (action === 'Report') this.reportComment(comment);
          else if (action === 'Reply') this.replyToComment(comment);
        }
      });
    },
    showReplyMenu(reply, parentComment) {
      const items = reply.isMine ? ['Delete', 'Report'] : ['Reply', 'Report'];
      uni.showActionSheet({
        itemList: items,
        success: (res) => {
          const action = items[res.tapIndex];
          if (action === 'Delete') this.deleteReply(reply, parentComment);
          else if (action === 'Report') this.reportComment(reply);
          else if (action === 'Reply') this.replyToReply(reply, parentComment);
        }
      });
    },
    reportComment(comment) {
      uni.showActionSheet({
        itemList: ['Spam', 'Illegal', 'Abuse', 'Attack', 'Other'],
        success: async (res) => {
          const reasons = ['Spam', 'Illegal', 'Abuse', 'Attack', 'Other'];
          try {
            const result = await reportComment(comment.id, reasons[res.tapIndex]);
            if (result.code === 200) uni.showToast({ title: 'Reported', icon: 'none' });
          } catch (error) {
            uni.showToast({ title: 'Report failed', icon: 'none' });
          }
        }
      });
    },
    toggleEmojiPanel() { this.showEmojiPanel = !this.showEmojiPanel; },
    insertEmoji(emoji) { this.commentText += emoji; },
    replyToComment(comment) {
      this.replyTarget = { type: 'comment', id: comment.id, userId: comment.userId, username: comment.username, parentId: comment.id };
    },
    replyToReply(reply, parentComment) {
      this.replyTarget = { type: 'reply', id: reply.id, userId: reply.userId, username: reply.username, parentId: parentComment.id, replyToId: reply.id };
    },
    cancelReply() { this.replyTarget = null; },
    handleInputFocus() { this.showEmojiPanel = false; },
    handleInputBlur() {},
    goBack() { uni.navigateBack(); },
    goHome() { uni.reLaunch({ url: '/pages/index/index' }); },
    goToUser() {
      const userId = this.post.userId || (this.post.user && this.post.user.id);
      if (userId) uni.navigateTo({ url: `/pages/user/home?id=${userId}` });
    },
    goToUserProfile(userId) {
      if (userId) uni.navigateTo({ url: `/pages/user/home?id=${userId}` });
    },
    previewImage(index) {
      const urls = this.post.images.map(img => this.getFullImageUrl(img));
      uni.previewImage({ current: index, urls });
    },
    async handleLike() {
      const newStatus = !this.isLiked;
      try {
        const res = await likePost(this.postId, newStatus);
        if (res.code === 200) {
          this.isLiked = newStatus;
          this.post.likes = Math.max(0, (this.post.likes || 0) + (newStatus ? 1 : -1));
        }
      } catch (error) {
        uni.showToast({ title: 'Action failed', icon: 'none' });
      }
    },
    async handleCollect() {
      const newStatus = !this.isCollected;
      try {
        const res = await collectPost(this.postId, newStatus);
        if (res.code === 200) {
          this.isCollected = newStatus;
          this.post.collectCount = Math.max(0, (this.post.collectCount || 0) + (newStatus ? 1 : -1));
          uni.showToast({ title: newStatus ? 'Collected' : 'Uncollected', icon: 'none' });
        }
      } catch (error) {
        uni.showToast({ title: 'Action failed', icon: 'none' });
      }
    },
    async handleLikeComment(comment) {
      const newStatus = !comment.isLiked;
      try {
        const res = await likeComment(comment.id, newStatus);
        if (res.code === 200) {
          comment.isLiked = newStatus;
          comment.likes = Math.max(0, (comment.likes || 0) + (newStatus ? 1 : -1));
        }
      } catch (error) {
        console.error(error);
      }
    },
    async handleLikeReply(reply) {
      const newStatus = !reply.isLiked;
      try {
        const res = await likeComment(reply.id, newStatus);
        if (res.code === 200) {
          reply.isLiked = newStatus;
          reply.likes = Math.max(0, (reply.likes || 0) + (newStatus ? 1 : -1));
        }
      } catch (error) {
        console.error(error);
      }
    },
    async handleSendComment() {
      if (!this.commentText.trim()) return;
      try {
        const commentData = { postId: this.postId, content: this.commentText };
        if (this.replyTarget) {
          commentData.parentId = this.replyTarget.parentId;
          commentData.replyToId = this.replyTarget.id;
        }
        const res = await addComment(commentData);
        if (res.code === 200) {
          const newComment = res.data;
          if (this.replyTarget) {
            const parentComment = this.comments.find(c => c.id === this.replyTarget.parentId);
            if (parentComment) {
              if (!parentComment.replies) parentComment.replies = [];
              parentComment.replies.push(newComment);
            }
          } else {
            this.comments.unshift(newComment);
          }
          this.commentText = '';
          this.replyTarget = null;
          uni.showToast({ title: 'Comment sent', icon: 'none' });
        }
      } catch (error) {
        uni.showToast({ title: 'Comment failed', icon: 'none' });
      }
    },
    deleteComment(comment) {
      uni.showModal({
        title: 'Confirm',
        content: 'Delete this comment?',
        success: (res) => {
          if (res.confirm) {
            const index = this.comments.findIndex(c => c.id === comment.id);
            if (index > -1) this.comments.splice(index, 1);
          }
        }
      });
    },
    deleteReply(reply, parentComment) {
      uni.showModal({
        title: 'Confirm',
        content: 'Delete this reply?',
        success: (res) => {
          if (res.confirm) {
            const index = parentComment.replies.findIndex(r => r.id === reply.id);
            if (index > -1) parentComment.replies.splice(index, 1);
          }
        }
      });
    }
  }
};
</script>

<style scoped>
.post-detail-page { min-height: 100vh; display: flex; flex-direction: column; }
.nav-header,.post-card,.comment-section,.emoji-panel,.reply-bar { background: linear-gradient(145deg, rgba(255,255,255,.96), rgba(247,247,245,.94)), repeating-linear-gradient(125deg, rgba(17,17,17,.02) 0, rgba(17,17,17,.02) 2rpx, transparent 2rpx, transparent 24rpx); border:1rpx solid rgba(17,17,17,.08); box-shadow:0 20rpx 56rpx rgba(17,17,17,.05); }
.nav-header { display:flex; align-items:center; justify-content:space-between; margin:18rpx 20rpx 0; padding:24rpx 28rpx; border-radius:28rpx; position:sticky; top:12rpx; backdrop-filter:blur(24rpx); -webkit-backdrop-filter:blur(24rpx); z-index:100; }
.back-btn,.nav-more { font-size:36rpx; color:#33312f; padding:10rpx 20rpx; }
.nav-title { font-size:32rpx; font-weight:bold; color:#171717; }
.loading-tip { text-align:center; padding:100rpx 0; color:#8d8d87; font-size:28rpx; }
.scroll-content { flex:1; height:0; padding:18rpx 20rpx 0; }
.post-card,.comment-section { border-radius:30rpx; padding:30rpx; margin-bottom:18rpx; }
.post-header,.vote-header,.comment-header,.comment-footer { display:flex; justify-content:space-between; align-items:center; }
.post-header { align-items:flex-start; margin-bottom:20rpx; }
.user-info,.comment-main,.reply-item { display:flex; }
.avatar,.comment-avatar,.reply-avatar { border-radius:50%; border:1rpx solid rgba(17,17,17,.08); background-color:#ececea; flex-shrink:0; }
.avatar { width:90rpx; height:90rpx; margin-right:20rpx; }
.comment-avatar { width:80rpx; height:80rpx; margin-right:20rpx; }
.reply-avatar { width:60rpx; height:60rpx; margin-right:15rpx; }
.user-meta,.comment-body,.reply-body { display:flex; flex-direction:column; min-width:0; flex:1; }
.user-name-row,.comment-user,.reply-user,.comment-actions,.comment-action,.reply-like,.sort-btn { display:flex; align-items:center; }
.username,.comment-username,.reply-username,.post-title,.comment-title { color:#171717; font-weight:700; }
.username,.comment-username { font-size:30rpx; }
.reply-username { font-size:26rpx; }
.post-time,.comment-time,.reply-time,.sort-btn,.vote-subtitle,.vote-hint,.empty-comment,.action-text,.comment-like-text { color:#8d8d87; }
.post-time,.comment-time { font-size:24rpx; }
.reply-time { font-size:22rpx; margin-left:auto; }
.more-btn,.comment-more,.reply-more { color:#8d8d87; }
.post-title { display:block; margin-bottom:12rpx; font-size:34rpx; line-height:1.4; }
.post-meta-tags { display:flex; flex-wrap:wrap; gap:10rpx; margin-bottom:14rpx; }
.category-chip,.tag-chip,.author-tag,.op-tag { padding:4rpx 12rpx; border-radius:999rpx; font-size:22rpx; border:1rpx solid rgba(17,17,17,.08); }
.category-chip,.tag-chip,.author-tag { background:rgba(17,17,17,.04); color:#4c4c48; }
.op-tag { background:rgba(17,17,17,.08); color:#1f1f1d; }
.content-text,.comment-content,.reply-content,.vote-option-text { color:#242422; line-height:1.6; white-space:pre-wrap; }
.content-text { display:block; font-size:30rpx; }
.comment-content { font-size:28rpx; margin-bottom:15rpx; }
.reply-content { font-size:26rpx; }
.post-images { display:flex; flex-wrap:wrap; gap:12rpx; margin-bottom:20rpx; }
.post-image { width:220rpx; height:220rpx; border-radius:20rpx; border:1rpx solid rgba(17,17,17,.08); }
.vote-card,.reply-list { margin-top:12rpx; margin-bottom:20rpx; padding:22rpx; border-radius:20rpx; background:rgba(17,17,17,.03); border:1rpx solid rgba(17,17,17,.08); }
.vote-title { font-size:28rpx; font-weight:700; color:#0f0f0f; }
.vote-options { display:flex; flex-direction:column; gap:14rpx; margin-top:16rpx; }
.vote-option { padding:16rpx; border-radius:16rpx; background:rgba(255,255,255,.82); border:1rpx solid rgba(17,17,17,.08); }
.vote-option.selected { background:repeating-linear-gradient(135deg, rgba(255,255,255,.12) 0, rgba(255,255,255,.12) 2rpx, transparent 2rpx, transparent 18rpx), #171717; border-color:#171717; }
.vote-option.selected .vote-option-text,.vote-option.selected .vote-option-count { color:#fff; }
.vote-option-row { display:flex; justify-content:space-between; align-items:center; margin-bottom:10rpx; }
.vote-option-count { font-size:24rpx; color:#666660; }
.vote-bar { height:10rpx; border-radius:999rpx; background:rgba(17,17,17,.08); overflow:hidden; }
.vote-bar-fill { height:100%; border-radius:999rpx; background:linear-gradient(90deg, #2c2c29, #6b6b65); }
.action-bar { display:flex; justify-content:space-around; padding-top:20rpx; border-top:1rpx solid rgba(17,17,17,.08); }
.action-item { display:flex; align-items:center; padding:15rpx 25rpx; }
.action-icon { font-size:32rpx; margin-right:8rpx; color:#666660; transition:transform .2s; }
.action-icon.active { color:#171717; }
.heart-icon { font-size:36rpx; }
.heart-icon.active,.comment-like-icon.liked,.reply-like-icon.liked { animation:heartBeat .3s ease; color:#8b3a3a; }
@keyframes heartBeat { 0%,100%{transform:scale(1)} 50%{transform:scale(1.3)} }
.comment-header { margin-bottom:30rpx; }
.comment-title { font-size:30rpx; }
.comment-item { margin-bottom:30rpx; }
.comment-user,.reply-user { flex-wrap:wrap; margin-bottom:10rpx; }
.comment-action { font-size:24rpx; color:#666660; margin-right:20rpx; }
.comment-like-icon,.reply-like-icon { font-size:28rpx; margin-right:5rpx; transition:transform .2s; }
.reply-to { color:#5d5d58; }
.empty-comment { text-align:center; padding:60rpx 0; font-size:28rpx; }
.bottom-space { height:120rpx; }
.comment-input-bar { position:fixed; bottom:0; left:0; right:0; display:flex; align-items:center; padding:16rpx 20rpx; padding-bottom:calc(16rpx + env(safe-area-inset-bottom)); background:rgba(243,243,241,.84); backdrop-filter:blur(24rpx); -webkit-backdrop-filter:blur(24rpx); border-top:1rpx solid rgba(17,17,17,.08); z-index:100; }
.home-icon,.emoji-btn,.image-btn { font-size:38rpx; color:#4f4f4b; }
.home-icon,.image-btn { margin-right:15rpx; }
.input-wrapper { flex:1; display:flex; align-items:center; background-color:rgba(255,255,255,.74); border-radius:999rpx; padding:15rpx 25rpx; margin-right:15rpx; border:1rpx solid rgba(17,17,17,.08); }
.comment-input { flex:1; font-size:28rpx; background:transparent; }
.send-btn { padding:15rpx 30rpx; background-color:rgba(17,17,17,.18); color:#fff; font-size:28rpx; border-radius:999rpx; }
.send-btn.active { background:repeating-linear-gradient(135deg, rgba(255,255,255,.14) 0, rgba(255,255,255,.14) 2rpx, transparent 2rpx, transparent 18rpx), linear-gradient(135deg, #1c1c1b 0%, #454540 100%); }
.reply-bar { position:fixed; bottom:120rpx; left:20rpx; right:20rpx; display:flex; justify-content:space-between; align-items:center; padding:15rpx 24rpx; border-radius:20rpx; z-index:99; }
.reply-hint,.cancel-reply { font-size:26rpx; color:#4b4b47; }
.emoji-panel { position:fixed; bottom:120rpx; left:20rpx; right:20rpx; height:400rpx; border-radius:28rpx; z-index:99; }
.emoji-content { height:100%; padding:20rpx; }
.emoji-grid { display:flex; flex-wrap:wrap; gap:20rpx; }
.emoji-item { font-size:50rpx; width:80rpx; height:80rpx; display:flex; align-items:center; justify-content:center; }
</style>
