export function responsavelSelecionado(form: FormData) {
  const valor = form.get("responsavel");
  return valor ? Number(valor) : null;
}
