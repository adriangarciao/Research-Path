import { BUDGETS, EXPERIENCES, FOCUSES, type Budget, type PathOptions } from '../api/types'
import { EXPERIENCE_HINT, EXPERIENCE_LABEL, FOCUS_HINT, FOCUS_LABEL } from '../lib/format'

interface Props {
  idPrefix: string
  value: PathOptions
  onChange: (next: PathOptions) => void
  disabled?: boolean
}

interface GroupProps<T extends string | number> {
  name: string
  legend: string
  options: readonly T[]
  value: T
  label: (v: T) => string
  hint?: string
  onSelect: (v: T) => void
  disabled?: boolean
}

function Segmented<T extends string | number>({ name, legend, options, value, label, hint, onSelect, disabled }: GroupProps<T>) {
  const hintId = `${name}-hint`
  return (
    <fieldset className="segmented" disabled={disabled} aria-describedby={hint ? hintId : undefined}>
      <legend>{legend}</legend>
      <div className="segmented__options">
        {options.map((o) => (
          <label key={String(o)} className="segmented__option">
            <input type="radio" name={name} value={String(o)} checked={o === value} onChange={() => onSelect(o)} />
            <span>{label(o)}</span>
          </label>
        ))}
      </div>
      {hint && (
        <p id={hintId} className="segmented__hint">
          {hint}
        </p>
      )}
    </fieldset>
  )
}

/** Level, number of papers and focus — the three settings a path can be regenerated with. */
export function OptionFields({ idPrefix, value, onChange, disabled }: Props) {
  return (
    <div className="option-fields">
      <Segmented
        name={`${idPrefix}-experience`}
        legend="Experience level"
        options={EXPERIENCES}
        value={value.experience}
        label={(v) => EXPERIENCE_LABEL[v]}
        hint={EXPERIENCE_HINT[value.experience]}
        onSelect={(experience) => onChange({ ...value, experience })}
        disabled={disabled}
      />
      <Segmented<Budget>
        name={`${idPrefix}-budget`}
        legend="Papers"
        options={BUDGETS}
        value={value.budget}
        label={(v) => String(v)}
        onSelect={(budget) => onChange({ ...value, budget })}
        disabled={disabled}
      />
      <Segmented
        name={`${idPrefix}-focus`}
        legend="Focus"
        options={FOCUSES}
        value={value.focus}
        label={(v) => FOCUS_LABEL[v]}
        hint={FOCUS_HINT[value.focus]}
        onSelect={(focus) => onChange({ ...value, focus })}
        disabled={disabled}
      />
    </div>
  )
}
