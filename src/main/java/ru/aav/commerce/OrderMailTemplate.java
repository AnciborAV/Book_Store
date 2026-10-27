package ru.aav.commerce;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.web.util.HtmlUtils;

/** Email content uses immutable purchase snapshots; user text is HTML-escaped. */
public final class OrderMailTemplate {

    private OrderMailTemplate() {}

    public record Content(String subject, String text, String html) {}

    public static Content render(CommerceDto.Detail detail) {
        var order = detail.order();
        String date = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
            .withZone(ZoneId.of("Europe/Moscow"))
            .format(order.createdAt());
        StringBuilder text = new StringBuilder(
            "Здравствуйте, " +
                order.buyerName() +
                "!\n\nСпасибо за заказ в книжном магазине «Переплёт».\nЗаказ № " +
                order.orderNumber() +
                "\nДата: " +
                date +
                " (МСК)\n\n"
        );
        StringBuilder rows = new StringBuilder();
        for (var item : detail.items()) {
            String sum = money(
                item.unitPrice().multiply(BigDecimal.valueOf(item.quantity()))
            );
            text.append(item.name())
                .append(" — ")
                .append(item.author())
                .append(": ")
                .append(item.quantity())
                .append(" × ")
                .append(money(item.unitPrice()))
                .append(" = ")
                .append(sum)
                .append("\n");
            rows.append(
                "<tr><td style='padding:12px;border-bottom:1px solid #ddd'>"
            )
                .append(escape(item.name()))
                .append("<br><small>")
                .append(escape(item.author()))
                .append("</small></td><td style='padding:12px'>")
                .append(item.quantity())
                .append("</td><td style='padding:12px'>")
                .append(money(item.unitPrice()))
                .append("</td><td style='padding:12px'>")
                .append(sum)
                .append("</td></tr>");
        }
        text.append("\nИтого: ")
            .append(money(order.total()))
            .append(
                "\nЭто подтверждение создания заказа, не кассовый чек. Онлайн-оплата не выполнялась.\n\nС уважением,\nКоманда книжного магазина «Переплёт»"
            );
        String html = """
        <!doctype html><html lang="ru"><head><meta charset="UTF-8"></head>
        <body style="margin:0;background:#f4f1e8;color:#263b32;font-family:Arial,sans-serif">
        <table role="presentation" width="100%%" cellspacing="0" cellpadding="0"><tr><td align="center" style="padding:24px 8px">
        <table role="presentation" width="600" style="width:100%%;max-width:600px;background:#fff" cellspacing="0" cellpadding="0">
        <tr><td style="background:#123e31;color:#fff;padding:28px;font-family:Georgia,serif;font-size:36px">переплёт<br><span style="font-family:Arial;font-size:12px;letter-spacing:3px">КНИЖНЫЙ МАГАЗИН</span></td></tr>
        <tr><td style="padding:28px"><h1 style="font-family:Georgia,serif;font-size:28px">Спасибо за ваш заказ!</h1>
        <p>Здравствуйте, %s!</p><p>Мы сохранили ваш заказ. Пусть каждая книга станет началом новой любимой истории.</p>
        <p style="overflow-wrap:anywhere">Заказ № %s<br>Дата: %s (МСК)</p>
        <table width="100%%" cellspacing="0" style="font-size:14px;text-align:left"><thead><tr style="background:#f4f1e8"><th>Книга</th><th>Кол-во</th><th>Цена</th><th>Сумма</th></tr></thead><tbody>%s</tbody></table>
        <p style="font-size:22px;text-align:right"><strong>Итого: %s</strong></p>
        <p style="font-size:12px;color:#666">Это подтверждение создания заказа, не кассовый чек. Онлайн-оплата не выполнялась. Актуальный статус доступен в разделе «Мои заказы».</p>
        <p>С уважением,<br><strong>Команда книжного магазина «Переплёт»</strong></p></td></tr>
        </table></td></tr></table></body></html>
        """.formatted(
            escape(order.buyerName()),
            escape(order.orderNumber()),
            date,
            rows,
            money(order.total())
        );
        return new Content(
            "Переплёт — ваш заказ № " + order.orderNumber(),
            text.toString(),
            html
        );
    }

    private static String money(BigDecimal value) {
        return value.setScale(2).toPlainString().replace('.', ',') + " ₽";
    }

    private static String escape(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }
}
