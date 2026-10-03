<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { StudyQuestion, StudyGrade } from '@/api/intelligence'
const props = defineProps<{ questions: StudyQuestion[]; grade?: StudyGrade; busy?: boolean; modelValue?: number[] }>()
const emit = defineEmits<{ submit: [number[]]; retry: []; 'update:modelValue': [number[]] }>()
const answers = ref<number[]>([])
watch(() => [props.questions, props.modelValue], () => {
  answers.value = props.modelValue?.length === props.questions.length ? [...props.modelValue] : props.questions.map(() => -1)
}, { immediate: true })
function choose(index: number, value: number) { answers.value[index] = value; emit('update:modelValue', [...answers.value]) }
const completed = computed(() => answers.value.filter(answer => answer >= 0).length)
function submit() { if (!props.busy && completed.value === props.questions.length) emit('submit', [...answers.value]) }
function retry() { answers.value = props.questions.map(() => -1); emit('update:modelValue', [...answers.value]); emit('retry') }
</script>
<template>
  <section v-if="grade" class="quiz-review" aria-label="测验结果">
    <div class="quiz-score"><strong>{{ grade.score }} / {{ grade.total }}</strong><span>题答对 · {{ Math.round(grade.score / Math.max(grade.total, 1) * 100) }}%</span><el-button @click="retry">再练一次</el-button></div>
    <article v-for="(item, index) in grade.results" :key="item.questionId" :class="['quiz-question', item.correct ? 'correct' : 'incorrect']"><h3>{{ index + 1 }}. {{ item.prompt }}</h3><p>{{ item.correct ? '回答正确' : '已加入错题本' }}</p><p>你的答案：{{ item.options[item.selectedIndex] || '未作答' }}</p><p><strong>正确答案：{{ item.options[item.correctIndex] }}</strong></p><div class="quiz-explanation">{{ item.explanation }}</div></article>
  </section>
  <form v-else @submit.prevent="submit">
    <p class="muted">已作答 {{ completed }} / {{ questions.length }}，提交后查看答案和解析。</p>
    <fieldset v-for="(question, index) in questions" :key="question.id" class="quiz-question" :disabled="busy"><legend>{{ index + 1 }}. {{ question.prompt }}</legend><label v-for="(option, optionIndex) in question.options" :key="optionIndex" :class="['quiz-option', { selected: answers[index] === optionIndex }]"><input type="radio" :name="`question-${question.id}`" :checked="answers[index] === optionIndex" :value="optionIndex" @change="choose(index, optionIndex)" /><span>{{ String.fromCharCode(65 + optionIndex) }}. {{ option }}</span></label></fieldset>
    <el-button native-type="submit" type="primary" :loading="busy" :disabled="!questions.length || completed !== questions.length || busy">提交答案</el-button>
  </form>
</template>
