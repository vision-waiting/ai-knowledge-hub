import { mount } from '@vue/test-utils'
import StudyQuiz from './StudyQuiz.vue'

const questions = [{ id: 1, prompt: '默认端口？', options: ['8080', '9000'] }, { id: 2, prompt: '支持格式？', options: ['PDF', 'EXE'] }]
const global = { stubs: { 'el-button': { props: ['disabled', 'nativeType'], template: '<button :disabled="disabled" :type="nativeType"><slot /></button>' } } }
it('requires every answer and submits selected indices without exposing a correct answer', async () => {
  const wrapper = mount(StudyQuiz, { props: { questions }, global })
  expect(wrapper.text()).not.toContain('正确答案')
  expect(wrapper.find('button').attributes('disabled')).toBeDefined()
  await wrapper.findAll('input')[0].setValue(true)
  await wrapper.find('form').trigger('submit')
  expect(wrapper.emitted('submit')).toBeUndefined()
  await wrapper.findAll('input')[3].setValue(true)
  await wrapper.find('form').trigger('submit')
  expect(wrapper.emitted('submit')?.[0]).toEqual([[0, 1]])
})
it('shows server grading and clears answers when repeating the quiz', async () => {
  const wrapper = mount(StudyQuiz, { props: { questions, grade: { score: 1, total: 2, results: [{ questionId: 2, prompt: '支持格式？', options: ['PDF', 'EXE'], selectedIndex: 1, correctIndex: 0, explanation: '资料列明支持 PDF', correct: false }] } }, global })
  expect(wrapper.text()).toContain('1 / 2')
  expect(wrapper.text()).toContain('正确答案：PDF')
  expect(wrapper.text()).toContain('资料列明支持 PDF')
  await wrapper.find('button').trigger('click')
  expect(wrapper.emitted('retry')).toHaveLength(1)
})
