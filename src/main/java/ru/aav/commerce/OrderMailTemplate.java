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
            rows.append("<tr><td style='padding:20px 0;border-bottom:1px solid #5a4c38;vertical-align:top;overflow-wrap:anywhere;word-break:break-word'>")
                .append("<span style='font-family:Georgia,serif;font-size:21px;line-height:1.4;color:#efe6d6'>")
                .append(escape(item.name()))
                .append("</span><br><span style='font-size:16px;line-height:1.6;color:#c1b69f'>")
                .append(escape(item.author()))
                .append("</span><br><span style='font-size:15px;line-height:1.8;color:#c1b69f'>")
                .append(item.quantity())
                .append(" × ")
                .append(money(item.unitPrice()))
                .append("</span></td><td align='right' style='width:112px;padding:20px 0 20px 12px;border-bottom:1px solid #5a4c38;vertical-align:top;font-size:18px;line-height:1.6;color:#efe6d6;white-space:nowrap'>")
                .append(sum)
                .append("</td></tr>");
        }
        text.append("\nИтого: ")
            .append(money(order.total()))
            .append(
                "\nЭто подтверждение создания заказа, не кассовый чек. Онлайн-оплата не выполнялась.\n\nС уважением,\nКоманда книжного магазина «Переплёт»"
            );
        String html = """
        <!doctype html>
        <html lang="ru"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1">
        <meta name="color-scheme" content="dark"><meta name="supported-color-schemes" content="dark">
        <title>Подтверждение заказа — Переплёт</title></head>
        <body bgcolor="#1b1916" style="margin:0;padding:0;background-color:#1b1916;color:#efe6d6;font-family:Arial,sans-serif;-webkit-text-size-adjust:100%%">
        <div style="display:none;font-size:1px;line-height:1px;max-height:0;max-width:0;opacity:0;overflow:hidden;mso-hide:all">Ваш заказ сохранён. Новая история начинается с первой страницы.</div>
        <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" bgcolor="#1b1916"><tr><td align="center" style="padding:28px 12px">
        <!--[if mso]><table role="presentation" width="600" cellspacing="0" cellpadding="0" border="0"><tr><td><![endif]-->
        <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" bgcolor="#25211c" style="width:100%%;max-width:600px;background-color:#25211c;border:1px solid #5a4c38">
        <tr><td style="height:4px;background-color:#c7a56d;font-size:1px;line-height:4px">&nbsp;</td></tr>
        <tr><td align="center" style="padding:32px 24px 28px;border-bottom:1px solid #5a4c38">
        <div style="font-family:Georgia,serif;font-size:40px;line-height:1.2;color:#efe6d6">переплёт<span style="color:#c7a56d">.</span></div>
        <div style="margin-top:12px;font-size:13px;line-height:1.6;letter-spacing:3px;color:#c7a56d">КНИЖНЫЙ МАГАЗИН</div>
        </td></tr>
        <tr><td style="padding:32px 24px 24px">
        <div style="font-size:13px;line-height:1.6;letter-spacing:2px;color:#c7a56d">ВАША КНИЖНАЯ ИСТОРИЯ</div>
        <h1 style="margin:14px 0 24px;font-family:Georgia,serif;font-size:32px;font-weight:normal;line-height:1.25;color:#efe6d6">Спасибо за ваш заказ!</h1>
        <p style="margin:0 0 14px;font-size:18px;line-height:1.6;overflow-wrap:anywhere;word-break:break-word">Здравствуйте, %s!</p>
        <p style="margin:0;font-size:17px;line-height:1.7;color:#c1b69f">Мы сохранили ваш заказ. Пусть эти книги подарят вам тихие вечера и удовольствие от каждой страницы.</p>
        </td></tr>
        <tr><td style="padding:0 24px 28px">
        <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" bgcolor="#1b1916" style="border-left:3px solid #c7a56d;background-color:#1b1916"><tr><td style="padding:18px 20px">
        <div style="font-size:14px;line-height:1.6;color:#c1b69f">ЗАКАЗ</div>
        <div style="margin:4px 0 8px;font-family:Georgia,serif;font-size:25px;line-height:1.4;color:#efe6d6;overflow-wrap:anywhere;word-break:break-word">№ %s</div>
        <div style="font-size:15px;line-height:1.6;color:#c1b69f">%s (МСК)</div>
        </td></tr></table>
        </td></tr>
        <tr><td style="padding:0 24px">
        <h2 style="margin:0 0 4px;font-family:Georgia,serif;font-size:25px;font-weight:normal;line-height:1.4;color:#efe6d6">На вашей книжной полке</h2>
        <table aria-label="Состав заказа" width="100%%" cellspacing="0" cellpadding="0" border="0" style="table-layout:fixed;text-align:left">
        <thead><tr><th scope="col" align="left" style="padding:12px 0;border-bottom:1px solid #5a4c38;font-size:14px;font-weight:normal;color:#c1b69f">Книга · количество · цена</th><th scope="col" align="right" style="width:112px;padding:12px 0 12px 12px;border-bottom:1px solid #5a4c38;font-size:14px;font-weight:normal;color:#c1b69f">Сумма</th></tr></thead><tbody>%s</tbody></table>
        <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0"><tr>
        <td style="padding:24px 0;font-size:18px;line-height:1.5;color:#efe6d6">Итого</td>
        <td align="right" style="padding:24px 0 24px 12px;font-family:Georgia,serif;font-size:27px;line-height:1.5;color:#c7a56d">%s</td>
        </tr></table>
        </td></tr>
        <tr><td style="padding:0 24px 28px">
        <p style="margin:0 0 18px;font-size:16px;line-height:1.7;color:#c1b69f">Актуальный статус доступен на сайте в разделе «Мои заказы».</p>
        <p style="margin:0;font-family:Georgia,serif;font-size:19px;line-height:1.6;color:#efe6d6">С уважением,<br>Команда книжного магазина «Переплёт»</p>
        </td></tr>
        <tr><td style="padding:24px;border-top:1px solid #5a4c38;background-color:#1b1916">
        <p style="margin:0;font-size:14px;line-height:1.7;color:#c1b69f">Это подтверждение создания заказа, не кассовый чек. Онлайн-оплата не выполнялась.</p>
        <p style="margin:18px 0 0;font-family:Georgia,serif;font-size:17px;line-height:1.6;font-style:italic;color:#c7a56d">Место, где начинаются новые истории.</p>
        </td></tr></table>
        <!--[if mso]></td></tr></table><![endif]-->
        </td></tr></table></body></html>
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
