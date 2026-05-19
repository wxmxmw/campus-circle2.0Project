<script>
	export default {
		mounted() {
			if (typeof document !== 'undefined') {
				document.title = 'Aether AI Campus'
			}
		},
		onLaunch: function() {
			console.log('App Launch')
			uni.hideTabBar()
		},
		onShow: function() {
			console.log('App Show')
			uni.hideTabBar()
		},
		onHide: function() {
			console.log('App Hide')
		}
	}
</script>

<style>
	:root {
		--app-bg: #d7d7d7;
		--surface-1: #ffffff;
		--surface-2: #f3f3f3;
		--surface-3: #e8e8e8;
		--text-1: #111111;
		--text-2: #4f4f4f;
		--text-3: #888888;
		--line-soft: rgba(17, 17, 17, 0.08);
		--line-strong: rgba(17, 17, 17, 0.16);
		--shadow-soft: 0 24rpx 70rpx rgba(17, 17, 17, 0.06);
		--shadow-float: 0 12rpx 36rpx rgba(17, 17, 17, 0.08);
		--accent: #111111;
		--accent-muted: #6d6d6d;
		--success: #1f1f1c;
		--danger: #8b3a3a;
	}

	page {
		background:
			radial-gradient(circle at top left, rgba(255, 255, 255, 0.74), rgba(255, 255, 255, 0) 32%),
			linear-gradient(135deg, rgba(255, 255, 255, 0.4) 0%, rgba(255, 255, 255, 0) 40%),
			repeating-linear-gradient(
				115deg,
				rgba(17, 17, 17, 0.02) 0,
				rgba(17, 17, 17, 0.02) 2rpx,
				transparent 2rpx,
				transparent 30rpx
			),
			var(--app-bg);
		color: var(--text-1);
	}

	view,
	text,
	input,
	textarea,
	button {
		box-sizing: border-box;
	}
</style>
