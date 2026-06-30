package labs.newrapaw.dlna.probe.admin

fun buildCommonFormScript(): String = """
    function showActionFeedback(message, isError) {
      const feedback = document.getElementById('action-feedback');
      if (!feedback) {
        return;
      }
      feedback.textContent = message;
      feedback.classList.toggle('error', !!isError);
      feedback.style.display = 'block';
      window.clearTimeout(window.__actionFeedbackTimer);
      window.__actionFeedbackTimer = window.setTimeout(() => {
        feedback.style.display = 'none';
        feedback.textContent = '';
        feedback.classList.remove('error');
      }, 3000);
    }

    async function submitControlForm(form, submitter) {
      const formData = new FormData(form, submitter);
      const response = await fetch(form.action, {
        method: form.method || 'POST',
        body: new URLSearchParams(formData),
        headers: { 'Accept': 'application/json' },
      });
      const payload = await response.json().catch(() => ({ ok: false, message: 'Unexpected response' }));
      if (!response.ok || !payload.ok) {
        showActionFeedback(payload.message || '请求失败', true);
        return;
      }
      showActionFeedback(payload.message || '操作已完成', false);
    }

    async function copyText(text) {
      if (navigator.clipboard && window.isSecureContext) {
        await navigator.clipboard.writeText(text);
        return;
      }

      const textarea = document.createElement('textarea');
      textarea.value = text;
      textarea.setAttribute('readonly', '');
      textarea.style.position = 'fixed';
      textarea.style.top = '-9999px';
      textarea.style.left = '-9999px';
      document.body.appendChild(textarea);
      textarea.focus();
      textarea.select();

      try {
        if (!document.execCommand('copy')) {
          throw new Error('复制失败');
        }
      } finally {
        document.body.removeChild(textarea);
      }
    }

    document.querySelectorAll('form[data-control-form]').forEach((form) => {
      form.addEventListener('submit', async (event) => {
        event.preventDefault();
        const submitter = event.submitter || form.querySelector('button[type="submit"], input[type="submit"]');
        try {
          await submitControlForm(form, submitter);
        } catch (error) {
          showActionFeedback(error.message || '请求失败', true);
        }
      });
    });
""".trimIndent()
