import { http } from './http'

export interface NoteInput { title: string; content: string; category?: string; sourceSessionId?: number | null }
export interface PersonalNote extends NoteInput { id: number; category: string; createdAt: string; updatedAt: string }
export const listNotes = (params: { keyword?: string; category?: string } = {}) => http.get('/notes', { params }) as Promise<PersonalNote[]>
export const listNoteCategories = () => http.get('/notes/categories') as Promise<string[]>
export const getNote = (id: number) => http.get(`/notes/${id}`) as Promise<PersonalNote>
export const createNote = (data: NoteInput) => http.post('/notes', data) as Promise<PersonalNote>
export const updateNote = (id: number, data: NoteInput) => http.put(`/notes/${id}`, data) as Promise<PersonalNote>
export const deleteNote = (id: number) => http.delete(`/notes/${id}`) as Promise<void>
