// Public source excerpt only. No database connection or visitor data is exposed.
// Snapshot of FeedbackService.create: keep this text in sync when the Java method changes.
const SOURCE = "@Transactional\npublic String create(String email, UUID requestId, Kind kind, String body, boolean consent) {\n    var user = accounts.lock(email);\n    // A retry after a lost response must not create a duplicate message.\n    var previous = jdbc.queryForList(\"SELECT id FROM site_feedback WHERE user_id=? AND request_id=?\",\n        String.class, user.getId(), requestId.toString());\n    if (!previous.isEmpty()) return previous.getFirst();\n    if (jdbc.queryForObject(\"SELECT COUNT(*) FROM site_feedback WHERE user_id=? AND created_at>?\",\n        Integer.class, user.getId(), Timestamp.from(Instant.now().minusSeconds(60))) > 0)\n        throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, \"Сообщение уже отправлено. Следующее можно отправить через минуту.\");\n    String id = UUID.randomUUID().toString();\n    jdbc.update(\"INSERT INTO site_feedback(id,user_id,request_id,author,kind,body,public_consent) VALUES(?,?,?,?,?,?,?)\",\n        id, user.getId(), requestId.toString(), user.getDisplayName(), kind.name(), body.strip(), consent);\n    String label = switch(kind) {case REVIEW -> \"Отзыв о сайте\"; case ISSUE -> \"Замечание\"; case IDEA -> \"Предложение\";};\n    mail.enqueue(\"SITE:\"+id,\"Новое сообщение о сайте\",\n        label+\"\\nАвтор: \"+user.getDisplayName()+\"\\n\\n\"+body.strip()\n        +\"\\n\\n\"+(consent ? \"Автор разрешил публикацию после проверки.\" : \"Сообщение только для администратора. Не публиковать.\")\n        +\"\\nОткройте кабинет администратора → Отзывы и уведомления → О сайте.\");\n    return id;\n}";

function element(tag, text, className) {
  const node = document.createElement(tag);
  if (text !== undefined) node.textContent = text;
  if (className) node.className = className;
  return node;
}

export function createFeedbackCode() {
  const details = element('details', undefined, 'feedback-source');
  details.append(element('summary', 'Как отзыв обрабатывается в Java'));
  details.append(element('p', 'Этот метод принимает сообщение, проверяет повторную отправку, сохраняет отзыв и ставит уведомление редактору в очередь. Текст отзыва передаётся в параметре body.', 'feedback-source-explanation'));
  const editor = element('div', undefined, 'feedback-source-editor');
  editor.append(element('div', 'FeedbackService.java · create(…) · Java 21', 'feedback-source-file'));
  const pre = element('pre'), code = element('code');
  const tokens = /("(?:\\.|[^"\\])*"|\/\/[^\n]*|@\w+|\b(?:public|String|boolean|var|if|return|throw|new|switch|case)\b)/g;
  let end = 0;
  for (const match of SOURCE.matchAll(tokens)) {
    code.append(document.createTextNode(SOURCE.slice(end, match.index)));
    const token = match[0];
    const kind = token.startsWith('"') ? 'string' : token.startsWith('//') ? 'comment' : token.startsWith('@') ? 'annotation' : 'keyword';
    code.append(element('span', token, `feedback-source-${kind}`));
    end = match.index + token.length;
  }
  code.append(document.createTextNode(SOURCE.slice(end)));
  pre.append(code); editor.append(pre); details.append(editor);
  details.append(element('p', 'Сохранение отзыва не означает публикацию: он станет виден посетителям после проверки редактором и при наличии согласия автора.', 'feedback-source-explanation'));
  return details;
}
