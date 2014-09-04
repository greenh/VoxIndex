#_ ( Copyright (c) 2013 Howard Green. All rights reserved.
                
     The use and distribution terms for this software are covered by the
     Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
     which can be found in the file epl-v10.html at the root of this distribution.
     By using this software in any fashion, you are agreeing to be bound by
     the terms of this license.
     
     You must not remove this notice, or any other, from this software.
     )

; this gets loaded by indexterous.util.vocalizer

#_ (* List of modifications to be applied on a token basis during the vocalization
      process.
      @p There are several situations that are dealt with here. First, by 
      default, all tokens that are all upper case are vocalized as individual
      letters, e.g. "API -> a p i". In some cases, however, this rule needs 
      to be inhibited or modified\: thus, "IEEE" -> "i triple e" or "SAT -> sat".
      @p The second situation occurs when acronyms and suchlike appear in
      lower or mixed case, and there's a need to replace it with a more
      appropriate vocalization\: thus, "api -> a p i", "ieee -> i triple e".
      )
(def token-mods
  (token-rep-map 
     (aaai -> triple a i)
     (acl -> a c l)
     (ajp -> a j p)
     (and -> and)
     (api -> a p i)
     (argb -> arg b)
     (awt -> a w t)
     (bmp -> b m p)
     (bluetooth -> blue tooth)
     (conl -> co n l)
     (conp -> co n p)
     (crl -> c r l)
     (crt -> c r t)
     (deque -> deck)
     (dp -> d p)
     (dgc -> d g c)
     (dnd -> d n d)
     (drm -> d r m)
     (dsig -> d sig)
     (egl -> e g l)
     (expspace -> e x p space)
     (exptime -> e x p time)
     (fifo -> fifo)
     (gl -> g l)
     (gps -> g p s)
     (gsm -> g s m)
     (gwt -> g w t)
     (html -> h t m l)
     (http -> h t t p)
     (id -> i d)
     (ieee -> i triple e)
     (ietf -> i e t f)
     (im -> i m)
     (imageio -> image i o)
     (io -> i o)
     (iso -> i s o)
     (jaspi -> j a s p i)
     (javax -> java x)
     (jgss -> j g s s )
     (jmx -> j m x)
     (jndi -> j n d i)
     (jpeg -> j peg)
     (js -> j s)
     (jso -> j s o)
     (json -> j son)
     (jsp -> j s p)
     (junit -> j unit)
     (jws -> j w s)
     (ldap -> l dap)
     (lifo -> lifo)
     (lru -> l r u)
     (mongodb -> mongo d b)
     (mtom -> m t o m)
     (msie -> m s i e)
     (mtp -> m t p)
     (ncf -> n c f)
     (nfc -> n f c)
     (ndef -> n def)
     (nio -> n i o)
     (npspace -> n p space)
     (ns -> n s)
     (omg -> o m g)
     ; (or -> or)
     (os -> o s)
     (osgi -> o s g i)
     (pspace -> p space)
     (qos -> q o s)
     (rfc -> r f c)
     (rtf -> r t f)
     (rtl -> r t l)
     (sat -> sat)
     (satplan -> sat plan)
     (slf -> s l f)
     (sms -> s m s)
     (soyc -> soy c)
     (spi -> s p i)
     (sql -> s q l)
     (sqlite -> s q l lite)
     (ssl -> s s l)
     (std -> s t d)
     (ui -> u i)
     (url -> u r l)
     (uri -> u r i)
     (xa -> x a )
     (xfer -> x fer)
     (xml -> x m l)))

#_ (* List of identifier @(u @(b prefixes)). The identifier vocalization process looks 
      for these prefixes at the start of tokens, and does the indicated replacement.
      This is mostly useful in situations, such as when dealing with all-lower-case
      package names, where normal tokenization processes don't operate.
      @p These are @(sc not) used in the non-identifier process.)
(def prefix-mods
  (prefix-rep-list
    (BSON -> b son)
    (CDATA -> c data)
    (CORBA -> corba)
    (DOM -> dom )
    (EGL -> e g l)
    (GL -> g l)
    (GLU -> g l u)
    (GZIP -> g zip)
    (Inet -> i net)
    (IDN -> i d n)
    (JSON -> j son)
    (JAAS -> jass )
    (JAX -> jax )
    (JPEG -> j peg )
    (JUNIT -> j unit)
    (OWL -> owl)
    (PKIX -> p kicks)
    (QoS -> q o s)
;   (OR -> or)  ;; kills 'org' thoroughly
;   (REF -> ref)
    (SAX -> sax)
    (SQL -> s q l)
    (SOAP -> soap)
    (SWRL -> swirl)
    (TIFF -> tiff)
    (UI -> u i)
    (UTF -> u t f)
  ))
