import { useId } from "react";

export function NewPasswordField({ label, name, errorId }: { label: string; name: string; errorId?: string }) {
  const id = useId(), hint = `${id}-requirements`;
  return <label><span id={`${id}-label`}>{label}</span><input name={name} type="password" autoComplete="new-password" minLength={10} aria-labelledby={`${id}-label`}
    aria-describedby={[hint, errorId].filter(Boolean).join(" ")} required />
    <small className="muted password-requirements" id={hint}>Use ao menos 10 caracteres, com uma letra maiúscula, uma minúscula e um número.</small>
  </label>;
}
