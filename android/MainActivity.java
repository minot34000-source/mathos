package io.github.minot34000_source.mathos;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** MATHOS pour Android : la page du site, embarquée dans l'application. Aucune permission, aucun accès au réseau. */
public class MainActivity extends Activity {
  private static final String ETIQUETTE = "MATHOS";
  private static final String PAGE = "file:///android_asset/index.html";
  private static final int CHOISIR = 1;
  private static final int ENREGISTRER = 2;

  // Dans la page, « télécharger » crée un lien vers un contenu gardé en mémoire.
  // On remet ce contenu à l'application, qui ouvre « Enregistrer sous ».
  private static final String PONT =
      "(function(){if(window.__pontMathos)return;window.__pontMathos=1;"
      + "var blocs={},creer=URL.createObjectURL;"
      + "URL.createObjectURL=function(o){var u=creer.apply(URL,arguments);if(o instanceof Blob)blocs[u]=o;return u};"
      + "function remettre(a){var n=a.getAttribute('download')||'MATHOS.txt',b=blocs[a.href];"
      + "(b?b.text():fetch(a.href).then(function(r){return r.text()}))"
      + ".then(function(t){MathosAndroid.enregistrer(n,t)}).catch(function(e){MathosAndroid.journal('pont : '+e)})}"
      + "var clic=HTMLAnchorElement.prototype.click;"
      + "HTMLAnchorElement.prototype.click=function(){if(this.hasAttribute('download')&&/^blob:/.test(this.href)){remettre(this);return}return clic.apply(this,arguments)};"
      + "document.addEventListener('click',function(e){var a=e.target&&e.target.closest?e.target.closest('a[download]'):null;"
      + "if(a&&/^blob:/.test(a.href)){e.preventDefault();remettre(a)}},true)})()";

  // Touche « Retour » du téléphone : revient à l'écran précédent de MATHOS s'il y en a un.
  private static final String RETOUR =
      "(function(){var b=document.querySelector('header.haut button[aria-label=\"Retour\"]');if(b){b.click();return true}return false})()";

  // Essai automatique (copie d'essai seulement) : la page s'affiche, garde des données, sait exporter une sauvegarde.
  private static final String ESSAI =
      "setTimeout(function(){try{"
      + "var t=(document.querySelector('#app')||document.body).innerText.replace(/\\s+/g,' ').slice(0,90),s;"
      + "try{localStorage.setItem('mathos:essai','1');s=localStorage.getItem('mathos:essai')==='1';localStorage.removeItem('mathos:essai')}catch(e){s='non ('+e+')'}"
      + "MathosAndroid.journal('page: '+document.title+' | '+t+' | largeur '+innerWidth+' contenu '+document.documentElement.scrollWidth+' stockage '+s+' sombre '+matchMedia('(prefers-color-scheme: dark)').matches);"
      + "var a=document.createElement('a');a.href=URL.createObjectURL(new Blob(['{\"essai\":true}'],{type:'text/plain;charset=utf-8'}));"
      + "a.download='MATHOS_essai.json';document.body.appendChild(a);a.click();a.remove()"
      + "}catch(e){MathosAndroid.journal('essai : '+e)}},2500)";

  private WebView vue;
  private ValueCallback<Uri[]> retourChoix;
  private String aEnregistrer;
  private boolean essai;

  @Override
  protected void onCreate(Bundle etat) {
    super.onCreate(etat);
    essai = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0 && getIntent().getBooleanExtra("essai", false);
    vue = new WebView(this);
    WebSettings reglages = vue.getSettings();
    reglages.setJavaScriptEnabled(true);
    reglages.setDomStorageEnabled(true);
    reglages.setAllowFileAccess(false);
    vue.setWebViewClient(new WebViewClient() {
      @Override
      public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest demande) {
        Uri u = demande.getUrl();
        if (u != null && u.toString().startsWith("file:///android_asset/")) return false;
        try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception e) { Log.i(ETIQUETTE, "lien non ouvert : " + u); }
        return true;
      }
      @Override
      public void onPageFinished(WebView v, String adresse) {
        v.evaluateJavascript(PONT, null);
        if (essai) v.evaluateJavascript(ESSAI, null);
      }
    });
    vue.setWebChromeClient(new WebChromeClient() {
      @Override
      public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> retour, FileChooserParams parametres) {
        if (retourChoix != null) retourChoix.onReceiveValue(null);
        retourChoix = retour;
        try {
          Intent choix = new Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
          startActivityForResult(Intent.createChooser(choix, "Choisir le fichier de sauvegarde"), CHOISIR);
          return true;
        } catch (Exception e) {
          retourChoix = null;
          return false;
        }
      }
      @Override
      public boolean onConsoleMessage(ConsoleMessage m) {
        Log.i(ETIQUETTE, "console : " + m.message() + " (ligne " + m.lineNumber() + ")");
        return true;
      }
    });
    vue.addJavascriptInterface(new Pont(), "MathosAndroid");
    setContentView(vue);
    vue.loadUrl(PAGE);
  }

  /** Ce que la page peut demander à l'application. */
  public final class Pont {
    @JavascriptInterface
    public void enregistrer(final String nom, final String texte) {
      runOnUiThread(new Runnable() {
        @Override
        public void run() { proposerEnregistrement(nom, texte); }
      });
    }
    @JavascriptInterface
    public void journal(String message) { Log.i(ETIQUETTE, String.valueOf(message)); }
  }

  private void proposerEnregistrement(String nom, String texte) {
    if (nom == null || texte == null) return;
    Log.i(ETIQUETTE, "enregistrer: " + nom + " (" + texte.length() + " caracteres)");
    if (essai) return;
    aEnregistrer = texte;
    String type = nom.endsWith(".json") ? "application/json" : nom.endsWith(".html") ? "text/html" : "text/plain";
    try {
      Intent ou = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(type).putExtra(Intent.EXTRA_TITLE, nom);
      startActivityForResult(ou, ENREGISTRER);
    } catch (Exception e) {
      aEnregistrer = null;
      dire("L'enregistrement n'est pas possible sur cet appareil.");
    }
  }

  @Override
  protected void onActivityResult(int demande, int resultat, Intent donnees) {
    super.onActivityResult(demande, resultat, donnees);
    if (demande == CHOISIR) {
      if (retourChoix != null) retourChoix.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultat, donnees));
      retourChoix = null;
    } else if (demande == ENREGISTRER) {
      String texte = aEnregistrer;
      aEnregistrer = null;
      if (resultat != RESULT_OK || donnees == null || donnees.getData() == null || texte == null) return;
      try (OutputStream sortie = getContentResolver().openOutputStream(donnees.getData())) {
        sortie.write(texte.getBytes(StandardCharsets.UTF_8));
        dire("Fichier enregistré.");
      } catch (Exception e) {
        dire("Le fichier n'a pas pu être enregistré.");
      }
    }
  }

  @Override
  public void onBackPressed() {
    vue.evaluateJavascript(RETOUR, new ValueCallback<String>() {
      @Override
      public void onReceiveValue(String reponse) { if (!"true".equals(reponse)) moveTaskToBack(true); }
    });
  }

  private void dire(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}
