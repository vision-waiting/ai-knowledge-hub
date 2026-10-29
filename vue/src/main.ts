import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { installComponents } from './components/elementPlus'
import 'element-plus/dist/index.css'
import './styles.css'
import './workspace.css'
import './tools.css'
import './studio.css'
import './book.css'
import './ink.css'
import './reading.css'
import App from './App.vue'
import router from './router'

createApp(App).use(createPinia()).use(router).use(installComponents).mount('#app')

