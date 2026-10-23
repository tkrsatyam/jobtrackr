package com.jobtrackr.email_ingestion_service.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GmailContentParserTest {

    @Test
    @DisplayName("Should strip CSS style tags and brackets from HTML")
    void testStripStyleTags() {
        String html = """
                <html>
                <head>
                    <style type="text/css">
                        body { font-family: Arial; font-size: 14px; }
                        @media screen and (max-width: 600px) { div { display: none !important; } }
                    </style>
                </head>
                <body>
                    <p>Dear Satyam,</p>
                    <p>Thank you for applying to the <b>Java Developer</b> role at <b>Stripe</b>.</p>
                </body>
                </html>
                """;

        String text = GmailContentParser.convertHtmlToText(html);

        assertThat(text).doesNotContain("font-family");
        assertThat(text).doesNotContain("{");
        assertThat(text).doesNotContain("}");
        assertThat(text).contains("Dear Satyam,");
        assertThat(text).contains("Java Developer");
        assertThat(text).contains("Stripe");
    }

    @Test
    @DisplayName("Should strip Outlook VML xml tags and HTML comments")
    void testStripOutlookVmlAndComments() {
        String html = """
                <!--[if gte mso 9]><xml>
                 <o:OfficeDocumentSettings>
                  <o:AllowPNG/>
                  <o:PixelsPerInch>96</o:PixelsPerInch>
                 </o:OfficeDocumentSettings>
                </xml><![endif]-->
                <div>Thank you for your recent application for the JAVA Developer role within MUFG Pension &amp; Market Services.</div>
                """;

        String text = GmailContentParser.convertHtmlToText(html);

        assertThat(text).doesNotContain("OfficeDocumentSettings");
        assertThat(text).doesNotContain("mso");
        assertThat(text).contains("Thank you for your recent application for the JAVA Developer role within MUFG Pension & Market Services.");
    }

    @Test
    @DisplayName("Should convert anchors into text with link URL")
    void testConvertAnchors() {
        String html = "<p>Please visit our <a href=\"https://careers.stripe.com/status\">Candidate Portal</a> to check status.</p>";
        String text = GmailContentParser.convertHtmlToText(html);

        assertThat(text).contains("Candidate Portal (https://careers.stripe.com/status)");
    }
}
