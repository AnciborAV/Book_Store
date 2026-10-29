package ru.aav.editorial;

import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.io.IOException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Editorial summaries, not publisher quotations. Never infer an uploaded PDF's edition. */
final class BookDescriptions {
    record Summary(String text, String source, boolean supplemental) {
        Summary(String text, String source) { this(text, source, false); }
    }
    private record Entry(String title, String author, String text, String source) {}
    private static final Map<String, Summary> SUPPLEMENTAL = load();

    private static Map<String, Summary> load() {
        try (var stream = BookDescriptions.class.getResourceAsStream("/editorial/java-descriptions.json")) {
            if (stream == null) throw new IllegalStateException("Missing Java descriptions");
            var entries = new ObjectMapper().readValue(stream, Entry[].class);
            var result = new HashMap<String, Summary>();
            for (var entry : entries) {
                if (entry.title() == null || entry.author() == null || entry.text() == null
                        || entry.text().isBlank() || entry.source() == null || !entry.source().startsWith("https://"))
                    throw new IllegalStateException("Invalid Java description");
                if (result.putIfAbsent(key(entry.title(), entry.author()),
                        new Summary(entry.text(), entry.source(), true)) != null)
                    throw new IllegalStateException("Duplicate Java description: " + entry.title());
            }
            return Map.copyOf(result);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read Java descriptions", ex);
        }
    }
    private static String key(String title, String author) {
        return title.strip().toLowerCase(Locale.ROOT) + "\n" + author.strip().toLowerCase(Locale.ROOT);
    }
    static Summary find(String title, String author) {
        String name = title.toLowerCase(Locale.ROOT), person = author.toLowerCase(Locale.ROOT);
        if ((name.contains("философия java") || name.contains("thinking in java"))
                && (person.contains("эккел") || person.contains("eckel") || name.contains("эккел"))) {
            return new Summary("""
                Java как способ мыслить

                «Философия Java» Брюса Эккеля посвящена пониманию языка через задачи, которые решает программист. Автор объясняет не только устройство языковых средств, но и причины их появления: какую проблему они помогают преодолеть и как влияют на построение программы.

                В центре книги — объектно-ориентированный подход. Эккель связывает изучение механизмов Java с развитием навыка рассуждать о коде, выбирать подходящие решения и видеть за отдельными конструкциями общую модель программы. Сопоставление подходов разных языков помогает осознанно пользоваться инструментами Java.

                Кому читать

                Книга адресована читателям, которым важно разобраться в логике программирования, особенно при небольшом опыте объектно-ориентированной разработки. Её полезно читать вдумчиво: запускать примеры, изменять условия задач и объяснять себе полученный результат.

                Редакционный ориентир

                Это чтение для формирования инженерного мышления: не спешить к готовому ответу, а разбираться в основаниях решения. Заметка опирается на описание полного четвёртого русского издания у издательства «Питер».
                """.strip(), "https://www.piter.com/collection/A20271/product/filosofiya-java-4-e-polnoe-izd");
        }
        if ((name.contains("java") && name.contains("полное руководство") || name.contains("java: the complete reference"))
                && (person.contains("шилдт") || person.contains("schildt") || name.contains("шилдт"))) {
            return new Summary("""
                От основ языка к библиотекам Java

                «Java. Полное руководство» Герберта Шилдта объединяет последовательное изучение языка со справочным изложением его возможностей. Материал помогает пройти путь от разработки и компиляции программы до работы с основными библиотеками и более сложными средствами Java.

                Что внутри

                Типы данных, переменные и управляющие конструкции переходят в рассмотрение классов, методов, наследования, интерфейсов и обработки исключений. Затем читатель знакомится с обобщениями, лямбда-выражениями, коллекциями, вводом-выводом, потоками данных и средствами параллельного программирования. Отдельное внимание уделяется библиотекам и примерам их применения.

                Кому читать

                Книга подходит для систематического освоения Java и возвращения к отдельным темам во время практической разработки. Удобный способ работы — изучить механизм, написать небольшой пример и затем найти его применение в собственном приложении.

                Об изданиях

                Заметка подготовлена по странице двенадцатого издания «Диалектика-Вильямс», ориентированного на Java SE 17: в нём рассматриваются записи, запечатанные классы и выражения switch. Содержание более ранних редакций отличается. Изучая новые версии Java, дополняйте книгу актуальной документацией к используемой версии JDK.
                """.strip(), "https://www.williamspublishing.com/Books/978-5-907458-86-4.html");
        }
        return SUPPLEMENTAL.getOrDefault(key(title, author), new Summary("", ""));
    }
    private BookDescriptions() {}
}
