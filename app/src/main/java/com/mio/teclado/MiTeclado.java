package com.mio.teclado;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

/** Teclado flotante de una sola fila (barra deslizable) que escribe en el campo enfocado. */
public class MiTeclado extends AccessibilityService {
    static final String SHIFT = "\u21e7", BORRAR = "\u232b", ENTER = "\u21b5";
    static final String LETRAS = "abcdefghijklmn\u00f1opqrstuvwxyz";
    static final String RESTO = "0123456789.,?!@-_/:;()";

    WindowManager wm;
    FrameLayout raiz;
    LinearLayout barra;
    TextView burbuja, teclaShift;
    WindowManager.LayoutParams lp;
    float escala = 1f;
    boolean mayus = false;
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
        raiz = new FrameLayout(this);

        barra = new LinearLayout(this);
        barra.setOrientation(LinearLayout.HORIZONTAL);
        barra.setGravity(Gravity.CENTER_VERTICAL);
        barra.setBackground(fondo(0xF0202124, 14));
        raiz.addView(barra, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        burbuja = new TextView(this);
        burbuja.setText("Aa");
        burbuja.setTextColor(Color.WHITE);
        burbuja.setTextSize(16);
        burbuja.setGravity(Gravity.CENTER);
        burbuja.setBackground(fondo(0xF01A73E8, 24));
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

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
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

        TextView asa = celda("\u2261", 30, null);          // arrastrar para mover
        arrastrar(asa, null);
        barra.addView(asa);
        barra.addView(celda("\u2013", 30, () -> minimizar(true)));   // minimizar

        HorizontalScrollView sv = new HorizontalScrollView(this);
        sv.setHorizontalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout fila = new LinearLayout(this);
        fila.setOrientation(LinearLayout.HORIZONTAL);

        teclaShift = celda(SHIFT, 34, () -> { mayus = !mayus; actualizarMayus(); });
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
        sv.addView(fila);
        barra.addView(sv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        barra.addView(celda("Esp", 42, () -> escribir(" ")));
        TextView del = celda(BORRAR, 36, null);
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
        actualizarMayus();
    }

    private TextView celda(String texto, float anchoDp, final Runnable accion) {
        TextView v = new TextView(this);
        v.setText(texto);
        v.setTextColor(Color.WHITE);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, (texto.length() > 1 ? 13 : 18) * escala);
        v.setGravity(Gravity.CENTER);
        v.setBackground(fondo(0xFF3C4043, 8));
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
        teclaShift.setBackground(fondo(mayus ? 0xFF1A73E8 : 0xFF3C4043, 8));
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

    private void enter() {
        AccessibilityNodeInfo n = campo();
        if (n == null) return;
        if (n.isMultiLine()) escribir("\n");
        else if (Build.VERSION.SDK_INT >= 30)
            n.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId());
    }

    // ---------- Utilidades ----------
    private int ancho() {
        return Math.min(px(330 * escala), getResources().getDisplayMetrics().widthPixels - px(8));
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
