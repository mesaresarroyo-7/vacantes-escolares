"use strict";

document.querySelectorAll("form[data-confirm]").forEach((form) => {
  form.addEventListener("submit", (event) => {
    if (!window.confirm(form.dataset.confirm)) event.preventDefault();
  });
});

document.querySelectorAll("textarea[data-count]").forEach((field) => {
  const count = document.getElementById(field.dataset.count);
  if (!count) return;
  const update = () => { count.textContent = String(field.value.length); };
  field.addEventListener("input", update);
  update();
});
