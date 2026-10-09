import { createApp } from 'vue'
import Companion from './Companion.vue'
import RigPreview from './RigPreview.vue'
import './style.css'
createApp(new URLSearchParams(location.search).has('rig')&&new URLSearchParams(location.search).has('debug')?RigPreview:Companion).mount('#app')
