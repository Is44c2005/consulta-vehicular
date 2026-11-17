package ec.udla.consulta.ant.parser;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import ec.udla.consulta.ant.domain.LicenciaInfo;
import ec.udla.consulta.ant.exception.AntRespuestaInvalidaException;

/**
 * Parser heurístico y aislado del HTML del portal de la ANT. Todo el conocimiento sobre la
 * estructura de la página vive aquí, de modo que si la ANT cambia su HTML solo se toca esta clase.
 *
 * <p>Una respuesta se considera <b>adecuada</b> si contiene al menos uno de estos elementos:
 * <ul>
 *   <li>los puntos de la licencia,</li>
 *   <li>una tabla de citaciones (encabezados th y filas td),</li>
 *   <li>el texto "no registra citaciones".</li>
 * </ul>
 * En cualquier otro caso (HTML vacío, página de mantenimiento, error) se lanza
 * {@link AntRespuestaInvalidaException}, que el circuit breaker cuenta como fallo.
 */
@Component
public class AntHtmlParser {

    private static final String MARCA_TABLA = "data-citaciones";
    private static final String NUMERO ="(\\d{1,3}(?:[.,]\\d{1,2})?)";
    private static final Pattern NUMERO_SOLO = Pattern.compile("^\\s*" + NUMERO + "\\s*$");
    private static final Pattern PUNTOS_ETIQUETA = Pattern.compile(
            "puntos?(?:\\s+de\\s+(?:la\\s+)?licencia)?(?:\\s+(?:actuales|disponibles|acumulados|vigentes))?\\s*[:=]?\\s*"
                    + NUMERO + "(?!\\d)");
    private static final Pattern PUNTOS_SUFIJO = Pattern.compile("(?<![\\d.,])" + NUMERO + "\\s*puntos?\\b");
    private static final List<String> SIN_CITACIONES = List.of(
            "no registra citaciones", "no registra infracciones", "no tiene citaciones", "no existen citaciones");

    public LicenciaInfo parse(String cedula, String html) {
        if (html == null || html.isBlank()) {
            throw new AntRespuestaInvalidaException("La ANT devolvió una respuesta vacía.");
        }
        Document doc = Jsoup.parse(html);

        List<Map<String, String>> citaciones = extraerCitaciones(doc);

        // Los puntos se buscan fuera de las tablas de citaciones (que pueden tener una columna "Puntos").
        Document sinTablas = doc.clone();
        sinTablas.select("table[" + MARCA_TABLA + "]").remove();
        Double puntos = extraerPuntos(sinTablas);

        String texto = normalizar(doc.text());
        boolean sinCitaciones = SIN_CITACIONES.stream().anyMatch(texto::contains);

        if (puntos == null && citaciones.isEmpty() && !sinCitaciones) {
            throw new AntRespuestaInvalidaException(
                    "La respuesta de la ANT no contiene puntos ni citaciones reconocibles.");
        }
        return new LicenciaInfo(cedula, puntos, citaciones);
    }

    // ------------------------------------------------------------------ puntos

    private Double extraerPuntos(Document doc) {
        // 1) Elementos cuyo id, class o name contenga "punto" y cuyo contenido sea solo un número.
        for (Element el : doc.select("[id*=punto], [class*=punto], [name*=punto]")) {
            String valor = el.hasAttr("value") ? el.attr("value") : el.ownText();
            Matcher m = NUMERO_SOLO.matcher(valor);
            if (m.matches()) {
                Double d = aNumero(m.group(1));
                if (d != null) {
                    return d;
                }
            }
        }
        // 2) Texto del tipo "Puntos: 30" o "Puntos de licencia 28,5".
        String texto = normalizar(doc.text());
        Double d = primerNumero(PUNTOS_ETIQUETA.matcher(texto));
        if (d != null) {
            return d;
        }
        // 3) Texto del tipo "30 puntos".
        return primerNumero(PUNTOS_SUFIJO.matcher(texto));
    }

    private static Double primerNumero(Matcher m) {
        while (m.find()) {
            Double d = aNumero(m.group(1));
            if (d != null) {
                return d;
            }
        }
        return null;
    }

    private static Double aNumero(String s) {
        try {
            double d = Double.parseDouble(s.replace(',', '.'));
            return (d >= 0 && d <= 100) ? d : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // -------------------------------------------------------------- citaciones

    private List<Map<String, String>> extraerCitaciones(Document doc) {
        List<Map<String, String>> filas = new ArrayList<>();
        for (Element tabla : doc.select("table")) {
            if (tabla.select("table").size() > 1) {
                continue; // tabla contenedora de maquetación
            }
            Elements trs = tabla.select("tr");
            int indiceEncabezado = -1;
            List<String> encabezados = List.of();
            for (int i = 0; i < trs.size(); i++) {
                Elements ths = trs.get(i).select("th");
                if (!ths.isEmpty()) {
                    indiceEncabezado = i;
                    encabezados = ths.eachText();
                    break;
                }
            }
            if (indiceEncabezado < 0 || encabezados.size() < 2) {
                continue;
            }

            List<Map<String, String>> filasTabla = new ArrayList<>();
            for (int i = indiceEncabezado + 1; i < trs.size(); i++) {
                Elements tds = trs.get(i).select("td");
                if (tds.isEmpty()) {
                    continue;
                }
                List<String> valores = tds.eachText();
                if (valores.stream().allMatch(String::isBlank)) {
                    continue;
                }
                if (valores.size() == 1 && normalizar(valores.get(0)).contains("no registra")) {
                    continue;
                }
                filasTabla.add(aFila(encabezados, valores));
            }
            if (!filasTabla.isEmpty()) {
                filas.addAll(filasTabla);
                tabla.attr(MARCA_TABLA, "1");
            }
        }
        return filas;
    }

    private static Map<String, String> aFila(List<String> encabezados, List<String> valores) {
        Map<String, String> fila = new LinkedHashMap<>();
        for (int c = 0; c < valores.size(); c++) {
            String col = c < encabezados.size() && !encabezados.get(c).isBlank()
                    ? encabezados.get(c).trim()
                    : "Columna " + (c + 1);
            String clave = col;
            for (int n = 2; fila.containsKey(clave); n++) {
                clave = col + " (" + n + ")";
            }
            fila.put(clave, valores.get(c).trim());
        }
        return fila;
    }

    private static String normalizar(String s) {
        String sinTildes = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(Locale.ROOT).replace(' ', ' ').replaceAll("\\s+", " ");
    }
}
