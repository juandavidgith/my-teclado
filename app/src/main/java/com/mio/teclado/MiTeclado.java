package com.mio.teclado;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MiTeclado extends AccessibilityService {
    static final String SHIFT = "\u21e7", BORRAR = "\u232b", ENTER = "\u21b5";
    static final String LETRAS = "abcdefghijklmn\u00f1opqrstuvwxyz";
    static final String RESTO = "0123456789.,?!@-_/:;()";
    static final String[] ENVIAR = {"Enviar", "Send", "Comentar", "Comment", "Publicar", "Post"};
    static final String[] OSCURO = {"Modo oscuro", "Tema oscuro", "Dark mode", "Dark theme"};

    // Temas: {fondo de la barra, teclas, texto, acento, burbuja}
    static final int[][] TEMAS = {
        {0xF0202124, 0xFF3C4043, 0xFFFFFFFF, 0xFF1A73E8, 0xF0000000},   // normal
        {0xF01E1E1E, 0xFF333333, 0xFFD4D4D4, 0xFF007ACC, 0xF0000000},   // VS Code oscuro
        {0xF0FFFFFF, 0xFFE8E8E8, 0xFF333333, 0xFF007ACC, 0xF0FFFFFF}    // VS Code claro
    };
    static final String[] NOMBRES_TEMA = {"Normal", "VS Code oscuro", "VS Code claro"};

    // Matematicas avanzadas: {lo que se ve en la tecla, lo que se escribe}
    static final String[][] MATE = {
        {"lim", "lim x\u2192 "}, {"\u2192", "\u2192"}, {"\u221E", "\u221E"},
        {"\u222B", "\u222B"}, {"\u222B dx", "\u222B f(x) dx"}, {"\u222Bab", "\u222B[a,b] f(x) dx"}, {"\u222C", "\u222C"},
        {"d/dx", "d/dx"}, {"\u2202", "\u2202"}, {"\u2207", "\u2207"}, {"\u0394", "\u0394"},
        {"\u2211", "\u2211(i=1\u2192n) "}, {"\u220F", "\u220F"}, {"\u221A", "\u221A("},
        {"x\u00B2", "\u00B2"}, {"x\u00B3", "\u00B3"}, {"x\u207F", "\u207F"}, {"^", "^"},
        {"sin", "sin("}, {"cos", "cos("}, {"tan", "tan("}, {"ln", "ln("}, {"log", "log("},
        {"\u2264", "\u2264"}, {"\u2265", "\u2265"}, {"\u2260", "\u2260"}, {"\u2248", "\u2248"},
        {"\u00B1", "\u00B1"}, {"\u00D7", "\u00D7"}, {"\u00F7", "\u00F7"}, {"\u00B0", "\u00B0"},
        {"\u03C0", "\u03C0"}, {"\u03B8", "\u03B8"}, {"\u03B1", "\u03B1"}, {"\u03B2", "\u03B2"},
        {"\u03BB", "\u03BB"}, {"\u03BC", "\u03BC"}, {"\u03C3", "\u03C3"}, {"\u03C9", "\u03C9"}
    };

    WindowManager wm;
    FrameLayout raiz;
    LinearLayout barra;
    TextView burbuja, teclaShift;
    WindowManager.LayoutParams lp;
    float escala = 1f;
    boolean mayus = false;
    Rect ultimaZona = new Rect();   // donde estaba el ultimo campo de texto usado
    int tema = 0;     // 0 = normal, 1 = VS Code oscuro, 2 = VS Code claro
    int pagina = 0;   // 0 = letras, 1 = funciones especiales, 2 = matematicas avanzadas
    final List<TextView> teclasLetra = new ArrayList<>();
    final Handler handler = new Handler(Looper.getMainLooper());
    final Runnable repetir = new Runnable() {
        @Override public void run() { borrar(); handler.postDelayed(this, 60); }
    };

    // ---------- Ciclo de vida ----------
    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        if (raiz != null) return;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        tema = getSharedPreferences("mi", MODE_PRIVATE).getInt("tema", 0);
        raiz = new FrameLayout(this);

        barra = new LinearLayout(this);
        barra.setOrientation(LinearLayout.HORIZONTAL);
        barra.setGravity(Gravity.CENTER_VERTICAL);
        barra.setBackground(fondo(TEMAS[tema][0], 14));
        arrastrar(barra, null);   // tambien se puede mover arrastrando los espacios entre teclas
        raiz.addView(barra, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        burbuja = new TextView(this);
        burbuja.setText("Aa");
        burbuja.setTextColor(TEMAS[tema][2]);
        burbuja.setTextSize(16);
        burbuja.setGravity(Gravity.CENTER);
        GradientDrawable negro = fondo(TEMAS[tema][4], 24);
        negro.setStroke(px(1), 0xFF5F6368);
        burbuja.setBackground(negro);
        burbuja.setVisibility(View.GONE);
        raiz.addView(burbuja, new FrameLayout.LayoutParams(px(48), px(48)));
        arrastrar(burbuja, () -> minimizar(false));

        construir();

        lp = new WindowManager.LayoutParams(ancho(), WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = px(8);
        lp.y = getResources().getDisplayMetrics().heightPixels / 2;
        wm.addView(raiz, lp);
        modoTeclado(false);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_VIEW_FOCUSED) return;
        AccessibilityNodeInfo s = event.getSource();
        if (s != null && s.isEditable() && !s.isPassword()) s.getBoundsInScreen(ultimaZona);
    }
    @Override public void onInterrupt() { }

    @Override public boolean onUnbind(Intent intent) {
        quitar();
        return super.onUnbind(intent);
    }

    private void quitar() {
        handler.removeCallbacks(repetir);
        modoTeclado(true);
        try { if (raiz != null) wm.removeView(raiz); } catch (Exception ignorada) { }
        raiz = null;
    }

    /** normal=true: vuelve el teclado del sistema; false: lo oculta mientras la barra esta abierta. */
    private void modoTeclado(boolean normal) {
        try {
            getSoftKeyboardController().setShowMode(normal ? SHOW_MODE_AUTO : SHOW_MODE_HIDDEN);
        } catch (Throwable ignorada) { }
    }

    // ---------- Interfaz ----------
    private void construir() {
        barra.removeAllViews();
        teclasLetra.clear();
        barra.setPadding(px(3), px(3), px(3), px(3));

        // Fn = funciones especiales
        TextView fn = celda("Fn", 28, () -> { pagina = pagina == 0 ? 1 : 0; construir(); });
        if (pagina != 0) { fn.setBackground(fondo(TEMAS[tema][3], 8)); fn.setTextColor(Color.WHITE); }
        barra.addView(fn);

        // el guion: tocarlo minimiza, arrastrarlo mueve la barra
        TextView min = celda("\u2013", 28, null);
        arrastrar(min, () -> minimizar(true));
        barra.addView(min);

        HorizontalScrollView sv = new HorizontalScrollView(this);
        sv.setHorizontalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout fila = new LinearLayout(this);
        fila.setOrientation(LinearLayout.HORIZONTAL);

        if (pagina == 1) {
            teclaShift = null;
            fila.addView(celda("\uD83C\uDF19 Modo oscuro", 110, this::modoOscuro));
            fila.addView(celda("\u222B Matem\u00e1ticas avanzadas", 175, () -> { pagina = 2; construir(); }));
            fila.addView(celda("Tema: " + NOMBRES_TEMA[tema], 155, this::cambiarTema));
            fila.addView(celda("Salir", 56, () -> performGlobalAction(GLOBAL_ACTION_HOME)));
            fila.addView(celda("Volver", 60, () -> { pagina = 0; construir(); }));
        } else if (pagina == 2) {
            teclaShift = null;
            for (String[] m : MATE) {
                final String texto = m[1];
                float w = Math.max(34, 11 * m[0].length() + 14);
                fila.addView(celda(m[0], w, () -> escribir(texto)));
            }
            fila.addView(celda("Volver", 60, () -> { pagina = 1; construir(); }));
        } else {
            teclaShift = celda(SHIFT, 34, () -> { mayus = !mayus; actualizarMayus(); });
            fila.addView(celda("Campo", 52, this::enfocarCampo));
            fila.addView(teclaShift);
            for (char c : LETRAS.toCharArray()) {
                final String t = String.valueOf(c);
                TextView k = celda(t, 34, () -> escribir(mayus ? t.toUpperCase(Locale.ROOT) : t));
                k.setTag(t);
                teclasLetra.add(k);
                fila.addView(k);
            }
            for (char c : RESTO.toCharArray()) {
                final String t = String.valueOf(c);
                fila.addView(celda(t, 34, () -> escribir(t)));
            }
            fila.addView(celda(ENTER, 40, this::enter));
            fila.addView(celda("A\u2212", 34, () -> cambiarTamano(-0.15f)));
            fila.addView(celda("A+", 34, () -> cambiarTamano(0.15f)));
        }
        sv.addView(fila);
        barra.addView(sv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        barra.addView(celda("Esp", 38, () -> escribir(" ")));
        TextView del = celda(BORRAR, 34, null);
        del.setOnTouchListener((w, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    w.setAlpha(0.6f);
                    borrar();
                    handler.postDelayed(repetir, 400);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    w.setAlpha(1f);
                    handler.removeCallbacks(repetir);
                    break;
            }
            return true;
        });
        barra.addView(del);

        TextView env = celda("Enviar", 52, this::enviar);   // boton azul fijo
        env.setBackground(fondo(TEMAS[tema][3], 8)); env.setTextColor(Color.WHITE);
        barra.addView(env);
        actualizarMayus();
    }

    private TextView celda(String texto, float anchoDp, final Runnable accion) {
        TextView v = new TextView(this);
        v.setText(texto);
        v.setTextColor(TEMAS[tema][2]);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP,
                (texto.codePointCount(0, texto.length()) > 1 ? 13 : 18) * escala);
        v.setGravity(Gravity.CENTER);
        v.setBackground(fondo(TEMAS[tema][1], 8));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(px(anchoDp * escala), px(40 * escala));
        p.setMargins(px(2), px(2), px(2), px(2));
        v.setLayoutParams(p);
        if (accion != null) {
            v.setOnClickListener(w -> accion.run());
            v.setOnTouchListener((w, e) -> {
                int a = e.getActionMasked();
                if (a == MotionEvent.ACTION_DOWN) w.setAlpha(0.6f);
                else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) w.setAlpha(1f);
                return false;
            });
        }
        return v;
    }

    private void actualizarMayus() {
        for (TextView k : teclasLetra) {
            String t = (String) k.getTag();
            k.setText(mayus ? t.toUpperCase(Locale.ROOT) : t);
        }
        if (teclaShift != null) {
            teclaShift.setBackground(fondo(mayus ? TEMAS[tema][3] : TEMAS[tema][1], 8));
            teclaShift.setTextColor(mayus ? Color.WHITE : TEMAS[tema][2]);
        }
    }

    private void cambiarTamano(float d) {
        escala = Math.max(0.6f, Math.min(1.8f, escala + d));
        construir();
        lp.width = ancho();
        clampX();
        wm.updateViewLayout(raiz, lp);
    }

    private void minimizar(boolean m) {
        barra.setVisibility(m ? View.GONE : View.VISIBLE);
        burbuja.setVisibility(m ? View.VISIBLE : View.GONE);
        lp.width = m ? WindowManager.LayoutParams.WRAP_CONTENT : ancho();
        if (!m) clampX();
        wm.updateViewLayout(raiz, lp);
        modoTeclado(m);   // minimizada: vuelve el teclado normal
    }

    private void clampX() {
        int pantalla = getResources().getDisplayMetrics().widthPixels;
        lp.x = Math.max(0, Math.min(lp.x, pantalla - lp.width));
    }

    /** Mover la ventana arrastrando la vista; si solo se toca (sin mover) ejecuta alTocar. */
    private void arrastrar(View vista, final Runnable alTocar) {
        vista.setOnTouchListener(new View.OnTouchListener() {
            int x0, y0;
            float tx, ty;
            boolean movio;
            @Override public boolean onTouch(View w, MotionEvent e) {
                DisplayMetrics dm = getResources().getDisplayMetrics();
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        x0 = lp.x; y0 = lp.y; tx = e.getRawX(); ty = e.getRawY(); movio = false;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - tx, dy = e.getRawY() - ty;
                        if (Math.abs(dx) > px(6) || Math.abs(dy) > px(6)) movio = true;
                        if (movio) {
                            lp.x = Math.max(0, Math.min((int) (x0 + dx), dm.widthPixels - raiz.getWidth()));
                            lp.y = Math.max(0, Math.min((int) (y0 + dy), dm.heightPixels - raiz.getHeight()));
                            wm.updateViewLayout(raiz, lp);
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                        if (!movio && alTocar != null) alTocar.run();
                        break;
                }
                return true;
            }
        });
    }

    // ---------- Escribir en el campo enfocado ----------
    private AccessibilityNodeInfo campo() {
        AccessibilityNodeInfo r = getRootInActiveWindow();
        AccessibilityNodeInfo f = r == null ? null : r.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (f == null || !f.isEditable()) return null;
        if (f.isPassword()) {
            Toast.makeText(this, "Campo de contrase\u00f1a: minimiza la barra y usa el teclado normal",
                    Toast.LENGTH_SHORT).show();
            return null;
        }
        return f;
    }

    private String textoActual(AccessibilityNodeInfo n) {
        if (Build.VERSION.SDK_INT >= 26 && n.isShowingHintText()) return "";
        CharSequence c = n.getText();
        return c == null ? "" : c.toString();
    }

    private int[] seleccion(AccessibilityNodeInfo n, String t) {
        int a = n.getTextSelectionStart(), b = n.getTextSelectionEnd();
        if (a < 0 || b < 0 || a > t.length() || b > t.length()) { a = t.length(); b = a; }
        return new int[]{Math.min(a, b), Math.max(a, b)};
    }

    private void poner(AccessibilityNodeInfo n, String texto, int cursor) {
        Bundle b = new Bundle();
        b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, texto);
        n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b);
        Bundle c = new Bundle();
        c.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, cursor);
        c.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, cursor);
        n.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, c);
    }

    private void escribir(String s) {
        AccessibilityNodeInfo n = campo();
        if (n == null) return;
        String t = textoActual(n);
        int[] sel = seleccion(n, t);
        poner(n, t.substring(0, sel[0]) + s + t.substring(sel[1]), sel[0] + s.length());
        if (mayus) { mayus = false; actualizarMayus(); }
    }

    private void borrar() {
        AccessibilityNodeInfo n = campo();
        if (n == null) return;
        String t = textoActual(n);
        int[] sel = seleccion(n, t);
        int a = sel[0], b = sel[1];
        if (a == b) {
            if (a == 0) return;
            a = t.offsetByCodePoints(a, -1);
        }
        poner(n, t.substring(0, a) + t.substring(b), a);
    }

    /** Enter normal: salto de linea en campos grandes, o la accion del teclado en campos de una linea. */
    private void enter() {
        AccessibilityNodeInfo n = campo();
        if (n == null) return;
        if (n.isMultiLine()) escribir("\n");
        else if (Build.VERSION.SDK_INT >= 30)
            n.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId());
    }

    private boolean esBotonEnviar(AccessibilityNodeInfo n) {
        String d = n.getContentDescription() == null ? "" : n.getContentDescription().toString().trim();
        String t = n.getText() == null ? "" : n.getText().toString().trim();
        for (String p : ENVIAR) if (p.equalsIgnoreCase(d) || p.equalsIgnoreCase(t)) return true;
        return false;
    }

    /** Busca en pantalla el boton Enviar (WhatsApp, etc.) y lo toca; si no lo encuentra, hace Enter. */
    private void enviar() {
        AccessibilityNodeInfo r = getRootInActiveWindow();
        if (r != null) {
            for (String palabra : ENVIAR) {
                List<AccessibilityNodeInfo> lista = r.findAccessibilityNodeInfosByText(palabra);
                if (lista == null) continue;
                for (AccessibilityNodeInfo n : lista) {
                    if (!esBotonEnviar(n)) continue;
                    AccessibilityNodeInfo c = n;
                    while (c != null && !c.isClickable()) c = c.getParent();
                    if (c != null && c.isEnabled() && c.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return;
                }
            }
        }
        enter();
    }

    /**
     * Android no deja a una app cambiar el modo oscuro directamente.
     * Abre los ajustes de pantalla, busca la opcion de modo oscuro, la toca sola y vuelve atras.
     */
    private void modoOscuro() {
        try {
            Intent i = new Intent(Settings.ACTION_DISPLAY_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS);
            return;
        }
        handler.postDelayed(() -> tocarOscuro(0), 900);
    }

    private void tocarOscuro(int intento) {
        AccessibilityNodeInfo r = getRootInActiveWindow();
        if (r != null) {
            for (String p : OSCURO) {
                List<AccessibilityNodeInfo> lista = r.findAccessibilityNodeInfosByText(p);
                if (lista == null) continue;
                for (AccessibilityNodeInfo n : lista) {
                    AccessibilityNodeInfo c = n;
                    while (c != null && !c.isClickable()) c = c.getParent();
                    if (c != null && c.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        handler.postDelayed(() -> performGlobalAction(GLOBAL_ACTION_BACK), 800);
                        return;
                    }
                }
            }
        }
        if (intento < 4) handler.postDelayed(() -> tocarOscuro(intento + 1), 600);
    }

    // ---------- Volver a habilitar la escritura ----------
    private void buscarCampos(AccessibilityNodeInfo n, List<AccessibilityNodeInfo> lista) {
        if (n == null) return;
        if (n.isEditable() && n.isVisibleToUser() && !n.isPassword()) lista.add(n);
        for (int i = 0; i < n.getChildCount(); i++) buscarCampos(n.getChild(i), lista);
    }

    /** Boton Campo: busca el campo de texto de la pantalla (el mas parecido al ultimo usado), lo activa y pone el cursor al final. */
    private void enfocarCampo() {
        List<AccessibilityNodeInfo> campos = new ArrayList<>();
        buscarCampos(getRootInActiveWindow(), campos);
        if (campos.isEmpty()) {
            Toast.makeText(this, "No encontr\u00e9 un campo de texto en esta pantalla", Toast.LENGTH_SHORT).show();
            return;
        }
        AccessibilityNodeInfo elegido = campos.get(0);
        int mejor = Integer.MAX_VALUE;
        Rect b = new Rect();
        for (AccessibilityNodeInfo c : campos) {
            c.getBoundsInScreen(b);
            int d = Math.abs(b.centerX() - ultimaZona.centerX()) + Math.abs(b.centerY() - ultimaZona.centerY());
            if (d < mejor) { mejor = d; elegido = c; }
        }
        elegido.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
        elegido.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        handler.postDelayed(() -> {
            AccessibilityNodeInfo f = campo();
            if (f == null) return;
            int fin = textoActual(f).length();
            Bundle cur = new Bundle();
            cur.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, fin);
            cur.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, fin);
            f.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, cur);
        }, 300);
    }

    // ---------- Temas ----------
    /** Cambia entre normal, VS Code oscuro y VS Code claro, y se acuerda del ultimo elegido. */
    private void cambiarTema() {
        tema = (tema + 1) % TEMAS.length;
        getSharedPreferences("mi", MODE_PRIVATE).edit().putInt("tema", tema).apply();
        barra.setBackground(fondo(TEMAS[tema][0], 14));
        GradientDrawable b = fondo(TEMAS[tema][4], 24);
        b.setStroke(px(1), 0xFF5F6368);
        burbuja.setBackground(b);
        burbuja.setTextColor(TEMAS[tema][2]);
        construir();
    }

    // ---------- Utilidades ----------
    private int ancho() {
        return Math.min(px(360 * escala), getResources().getDisplayMetrics().widthPixels - px(8));
    }

    private int px(float dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable fondo(int color, float radioDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(px(radioDp));
        return g;
    }
}
