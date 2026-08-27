export function downloadFile(url: string) {
  const a = document.createElement('a');
  a.href = url;
  a.target = '_blank';
  document.body.appendChild(a);
  a.click();
  a.remove();
}
