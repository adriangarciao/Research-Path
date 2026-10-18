import { useId, useRef, useState, type FormEvent } from 'react'
import { DEFAULT_OPTIONS, MAX_TOPIC_LENGTH, type PathOptions } from '../api/types'
import { OptionFields } from './OptionFields'

interface Props {
  onSubmit: (topic: string, options: PathOptions) => void
  pending: boolean
  initialTopic?: string
}

/** Topic + settings. Client checks mirror the server's; the server still validates everything. */
export function SearchForm({ onSubmit, pending, initialTopic = '' }: Props) {
  const [topic, setTopic] = useState(initialTopic)
  const [options, setOptions] = useState<PathOptions>(DEFAULT_OPTIONS)
  const [error, setError] = useState<string | null>(null)
  const inputRef = useRef<HTMLInputElement>(null)
  const id = useId()

  function submit(e: FormEvent) {
    e.preventDefault()
    if (pending) return
    const clean = topic.trim().replace(/\s+/g, ' ')
    if (!clean) {
      setError('Enter a research topic, for example “graph neural networks”.')
      inputRef.current?.focus()
      return
    }
    if (clean.length > MAX_TOPIC_LENGTH) {
      setError(`Keep the topic under ${MAX_TOPIC_LENGTH} characters.`)
      inputRef.current?.focus()
      return
    }
    setError(null)
    onSubmit(clean, options)
  }

  const errorId = `${id}-error`
  return (
    <form className="search" onSubmit={submit} noValidate aria-busy={pending}>
      <label className="search__label" htmlFor={`${id}-topic`}>
        What do you want to learn about?
      </label>
      <div className="search__row">
        <input
          ref={inputRef}
          id={`${id}-topic`}
          className="search__input"
          type="text"
          value={topic}
          maxLength={MAX_TOPIC_LENGTH}
          placeholder="e.g. reinforcement learning in robotics"
          autoComplete="off"
          disabled={pending}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? errorId : `${id}-count`}
          onChange={(e) => {
            setTopic(e.target.value)
            if (error) setError(null)
          }}
        />
        <button className="button button--primary" type="submit" disabled={pending}>
          {pending ? 'Building…' : 'Build reading path'}
        </button>
      </div>
      <p className="search__meta">
        {error ? (
          <span id={errorId} className="field-error" role="alert">
            {error}
          </span>
        ) : (
          <span id={`${id}-count`}>
            {topic.length}/{MAX_TOPIC_LENGTH}
          </span>
        )}
      </p>
      <OptionFields idPrefix={`${id}-new`} value={options} onChange={setOptions} disabled={pending} />
    </form>
  )
}
