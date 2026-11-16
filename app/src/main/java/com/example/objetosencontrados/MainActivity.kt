package com.example.objetosencontrados

import android.os.Bundle
import android.net.Uri
import android.app.DatePickerDialog
import androidx.compose.ui.platform.LocalContext
import android.widget.ImageView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.viewinterop.AndroidView
import java.util.Calendar
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.KeyboardCapitalization
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Identidad visual original de ObjetosPerdidosUNSA
private val Guinda = Color(0xFF70264B)
private val GuindaOscuro = Color(0xFF541B38)
private val Lila = Color(0xFFEDE3F5)
private val Morado = Color(0xFF76518D)
private val Fondo = Color(0xFFF8F6F9)
private val Blanco = Color.White
private val Texto = Color(0xFF302A33)
private val Gris = Color(0xFF77717B)
private val Borde = Color(0xFFE7DCE8)
private val Verde = Color(0xFF346548)
private val Rojo = Color(0xFF9B2525)

private object Rutas {
    const val ACCESO = "acceso"
    const val INICIO = "inicio"
    const val PERDIDOS = "objetos_perdidos"
    const val TIPO_REPORTE = "tipo_reporte"
    const val FORM_HALLAZGO = "form_hallazgo"
    const val FORM_PERDIDO = "form_perdido"
    const val REVISION = "revision"
    const val EXITO = "exito"
    const val MIS_REPORTES = "mis_reportes"
    const val PERFIL = "perfil"
    const val DETALLE = "detalle/{codigo}"          // ruta con argumento
    fun detalle(codigo: String) = "detalle/$codigo"
}

// VERIFICACIÓN SIMULADA DEL CUI (prototipo sin base de datos).
// Estudiantes de demostración (CUI → nombre).
private val estudiantesDemo = mapOf(
    "20240001" to "Milene",
    "20240002" to "Laura",
    "20240003" to "Ysabel"
)

// Opción 2: validar que el CUI empiece con un año de ingreso razonable.
// Si el formato real del CUI no es así, cambiar a false.
private const val VALIDAR_ANIO_INGRESO = true

/** Devuelve un mensaje de error si el CUI completo no tiene un formato válido; null si es válido. */
private fun errorFormatoCui(cui: String): String? {
    if (cui.length != 8 || !VALIDAR_ANIO_INGRESO) return null
    val anio = cui.take(4).toIntOrNull() ?: return "El CUI solo debe contener números."
    val anioActual = Calendar.getInstance().get(Calendar.YEAR)
    return if (anio in 1990..anioActual) null
    else "El CUI debe empezar con tu año de ingreso (ej. 2024…)."
}

data class ReporteUNSA(
    val codigo: String,
    val clase: String, // "Encontrado" o "Perdido"
    val nombre: String,
    val ubicacion: String,
    val descripcion: String,
    val fecha: String,
    val categoria: String = "",
    val fotoUri: String? = null,
    val entrega: String = "Aún no entregado",
    val estado: String = "Activo",
    val cuiAutor: String = ""
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Guinda,
                    onPrimary = Blanco,
                    secondary = Morado,
                    background = Fondo,
                    surface = Blanco
                )
            ) {
                // safeDrawingPadding evita que la barra de estado, la de navegación
                // o el teclado tapen el contenido (Android 15+ es edge-to-edge).
                Surface(Modifier.fillMaxSize().safeDrawingPadding(), color = Fondo) {
                    AppObjetosPerdidosUNSA()
                }
            }
        }
    }
}

@Composable
fun AppObjetosPerdidosUNSA() {
    // NAVEGACIÓN: NavController administra el historial (back stack)
    // y NavHost relaciona cada ruta con su pantalla.
    val navController = rememberNavController()

    // rememberSaveable conserva los datos al rotar la pantalla.
    var cui by rememberSaveable { mutableStateOf("") }
    var nombreSesion by rememberSaveable { mutableStateOf("") }
    var nombreIngresado by rememberSaveable { mutableStateOf("") }   // opción 3

    // Los reportes se mantienen en memoria durante la ejecución de la app.
    val reportes = remember { mutableStateListOf<ReporteUNSA>() }

    var claseFormulario by rememberSaveable { mutableStateOf("Encontrado") }
    var categoriaObjeto by rememberSaveable { mutableStateOf("") }
    var fotoHallazgoUri by rememberSaveable { mutableStateOf<String?>(null) }
    var nombreObjeto by rememberSaveable { mutableStateOf("") }
    var ubicacion by rememberSaveable { mutableStateOf("") }
    var descripcion by rememberSaveable { mutableStateOf("") }
    var fecha by rememberSaveable { mutableStateOf("") }
    var entrega by rememberSaveable { mutableStateOf("Aún no entregado") }
    var errorFormulario by rememberSaveable { mutableStateOf("") }
    var reporteCreado by remember { mutableStateOf<ReporteUNSA?>(null) }

    fun limpiarFormulario() {
        nombreObjeto = ""
        ubicacion = ""
        descripcion = ""
        fecha = ""
        entrega = "Aún no entregado"
        categoriaObjeto = ""
        fotoHallazgoUri = null
        errorFormulario = ""
    }

    NavHost(navController = navController, startDestination = Rutas.ACCESO) {

        composable(Rutas.ACCESO) {
            PantallaAcceso(
                cui = cui,
                nombre = nombreIngresado,
                nombreConocido = estudiantesDemo[cui],
                onCui = { cui = it.filter(Char::isDigit).take(8) },
                // Solo letras y espacios, máximo 30 caracteres.
                onNombre = { nombreIngresado = it.filter { c -> c.isLetter() || c == ' ' }.take(30) },
                onIngresar = {
                    if (cui.length == 8 && errorFormatoCui(cui) == null) {
                        // Si el CUI está en la lista se usa ese nombre; si no, el que escribió el usuario.
                        nombreSesion = estudiantesDemo[cui] ?: nombreIngresado.trim()
                        // Se retira "acceso" del historial: Atrás no regresa al login.
                        navController.navigate(Rutas.INICIO) {
                            popUpTo(Rutas.ACCESO) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(Rutas.INICIO) {
            PantallaInicio(
                nombre = nombreSesion,
                onPerdidos = { navController.navigate(Rutas.PERDIDOS) },
                onReportar = { navController.navigate(Rutas.TIPO_REPORTE) },
                onMisReportes = { navController.navigate(Rutas.MIS_REPORTES) },
                onPerfil = { navController.navigate(Rutas.PERFIL) }
            )
        }

        composable(Rutas.PERDIDOS) {
            PantallaObjetosPerdidos(
                reportes = reportes.filter { it.clase == "Perdido" && it.estado == "Activo" },
                onVolver = { navController.popBackStack() },
                onReportar = { navController.navigate(Rutas.TIPO_REPORTE) },
                onAbrir = { navController.navigate(Rutas.detalle(it.codigo)) }
            )
        }

        composable(Rutas.TIPO_REPORTE) {
            PantallaTipoReporte(
                onVolver = { navController.popBackStack() },
                onEncontrado = {
                    claseFormulario = "Encontrado"
                    limpiarFormulario()
                    navController.navigate(Rutas.FORM_HALLAZGO)
                },
                onPerdido = {
                    claseFormulario = "Perdido"
                    limpiarFormulario()
                    navController.navigate(Rutas.FORM_PERDIDO)
                }
            )
        }

        composable(Rutas.FORM_HALLAZGO) {
            FormularioHallazgo(
                nombre = nombreObjeto,
                categoria = categoriaObjeto,
                fotoUri = fotoHallazgoUri,
                ubicacion = ubicacion,
                descripcion = descripcion,
                fecha = fecha,
                entrega = entrega,
                error = errorFormulario,
                onNombre = { nombreObjeto = it; errorFormulario = "" },
                onCategoria = { categoriaObjeto = it; errorFormulario = "" },
                onFotoSeleccionada = { fotoHallazgoUri = it; errorFormulario = "" },
                onUbicacion = { ubicacion = it; errorFormulario = "" },
                onDescripcion = { descripcion = it; errorFormulario = "" },
                onFecha = { fecha = it; errorFormulario = "" },
                onEntrega = { entrega = it },
                onVolver = { navController.popBackStack() },
                onRevisar = {
                    errorFormulario = when {
                        categoriaObjeto.isBlank() -> "Selecciona el tipo de objeto."
                        else -> validarFormulario(nombreObjeto, ubicacion, descripcion, fecha)
                    }
                    if (errorFormulario.isEmpty()) navController.navigate(Rutas.REVISION)
                }
            )
        }

        composable(Rutas.FORM_PERDIDO) {
            FormularioPerdido(
                nombre = nombreObjeto,
                ubicacion = ubicacion,
                descripcion = descripcion,
                fecha = fecha,
                error = errorFormulario,
                onNombre = { nombreObjeto = it; errorFormulario = "" },
                onUbicacion = { ubicacion = it; errorFormulario = "" },
                onDescripcion = { descripcion = it; errorFormulario = "" },
                onFecha = { fecha = it; errorFormulario = "" },
                onVolver = { navController.popBackStack() },
                onRevisar = {
                    errorFormulario = validarFormulario(nombreObjeto, ubicacion, descripcion, fecha)
                    if (errorFormulario.isEmpty()) navController.navigate(Rutas.REVISION)
                }
            )
        }

        composable(Rutas.REVISION) {
            PantallaRevision(
                clase = claseFormulario,
                nombre = nombreObjeto,
                ubicacion = ubicacion,
                descripcion = descripcion,
                fecha = fecha,
                categoria = if (claseFormulario == "Encontrado") categoriaObjeto else "",
                fotoUri = if (claseFormulario == "Encontrado") fotoHallazgoUri else null,
                entrega = if (claseFormulario == "Encontrado") entrega else "No aplica",
                // Regresa al formulario correcto (hallazgo o perdido) con los datos intactos.
                onEditar = { navController.popBackStack() },
                onConfirmar = {
                    val nuevo = ReporteUNSA(
                        codigo = "UNSA-${if (claseFormulario == "Encontrado") "E" else "P"}-${(reportes.size + 1).toString().padStart(4, '0')}",
                        clase = claseFormulario,
                        nombre = nombreObjeto.trim(),
                        ubicacion = ubicacion.trim(),
                        descripcion = descripcion.trim(),
                        fecha = fecha.trim(),
                        categoria = if (claseFormulario == "Encontrado") categoriaObjeto else "",
                        fotoUri = if (claseFormulario == "Encontrado") fotoHallazgoUri else null,
                        entrega = if (claseFormulario == "Encontrado") entrega else "No aplica",
                        cuiAutor = cui
                    )
                    reportes.add(0, nuevo)
                    reporteCreado = nuevo
                    limpiarFormulario()
                    // Se retiran formulario y revisión del historial para evitar reenvíos.
                    navController.navigate(Rutas.EXITO) {
                        popUpTo(Rutas.INICIO)
                    }
                }
            )
        }

        composable(Rutas.EXITO) {
            PantallaExito(
                reporte = reporteCreado,
                onVerReportes = {
                    navController.navigate(Rutas.MIS_REPORTES) { popUpTo(Rutas.INICIO) }
                },
                onInicio = { navController.popBackStack(Rutas.INICIO, inclusive = false) }
            )
        }

        composable(Rutas.MIS_REPORTES) {
            PantallaMisReportes(
                reportes = reportes.filter { it.cuiAutor == cui },
                onVolver = { navController.popBackStack() },
                onNuevo = { navController.navigate(Rutas.TIPO_REPORTE) },
                onAbrir = { navController.navigate(Rutas.detalle(it.codigo)) }
            )
        }

        // PATRÓN LISTA-DETALLE: la lista envía el código del reporte como argumento.
        composable(
            route = Rutas.DETALLE,
            arguments = listOf(navArgument("codigo") { type = NavType.StringType })
        ) { entrada ->
            val codigo = entrada.arguments?.getString("codigo").orEmpty()
            PantallaDetalle(
                reporte = reportes.firstOrNull { it.codigo == codigo },
                onVolver = { navController.popBackStack() }
            )
        }

        composable(Rutas.PERFIL) {
            PantallaPerfil(
                nombre = nombreSesion,
                cui = cui,
                onVolver = { navController.popBackStack() },
                onCerrarSesion = {
                    cui = ""
                    nombreSesion = ""
                    nombreIngresado = ""
                    limpiarFormulario()
                    reporteCreado = null
                    // Se limpia todo el historial: Atrás no vuelve a la sesión cerrada.
                    navController.navigate(Rutas.ACCESO) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                }
            )
        }
    }
}

private fun validarFormulario(nombre: String, ubicacion: String, descripcion: String, fecha: String): String {
    val letras = Regex("[\\p{L}]")
    return when {
        nombre.trim().length < 2 || !letras.containsMatchIn(nombre) ->
            "Escribe un nombre válido usando letras; no se aceptan solo números."
        ubicacion.trim().length < 3 || !letras.containsMatchIn(ubicacion) ->
            "Escribe un lugar válido usando palabras; no se aceptan solo números."
        descripcion.trim().length < 10 || letras.findAll(descripcion).count() < 5 ->
            "La descripción debe tener al menos 10 caracteres y 5 letras."
        descripcion.length > 250 ->
            "La descripción no puede superar los 250 caracteres."
        fecha.trim().isEmpty() ->
            "Selecciona la fecha del hallazgo o pérdida."
        else -> ""
    }
}

@Composable
private fun PantallaAcceso(
    cui: String, nombre: String, nombreConocido: String?,
    onCui: (String) -> Unit, onNombre: (String) -> Unit, onIngresar: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var cargando by remember { mutableStateOf(false) }
    val errorFormato = errorFormatoCui(cui)
    val cuiValido = cui.length == 8 && errorFormato == null
    val faltan = 8 - cui.length
    // Opción 3: si el CUI no está en la lista, se pide el nombre.
    val pideNombre = cuiValido && nombreConocido == null
    val nombreValido = nombre.trim().length >= 2
    val listo = cuiValido && (nombreConocido != null || nombreValido)

    // Evento de ingreso: se usa desde el botón y desde la tecla ✓ del teclado.
    fun ingresar() {
        if (!listo || cargando) return
        cargando = true
        scope.launch {
            delay(800)   // breve verificación: el usuario ve que el sistema está trabajando
            cargando = false
            onIngresar()
        }
    }

    Column(
        Modifier.fillMaxSize().background(Fondo).verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.height(40.dp))
        Box(
            Modifier.size(88.dp).background(Guinda, CircleShape),
            contentAlignment = Alignment.Center
        ) { Text("EPIS", color = Blanco, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp) }
        Spacer(Modifier.height(22.dp))
        Text("OBJETOS PERDIDOS UNSA", color = Guinda, fontSize = 23.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text("Universidad Nacional de San Agustín de Arequipa", color = Gris, fontSize = 13.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(34.dp))
        Text("¡Te damos la bienvenida!", color = Texto, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Ingresa tu CUI para continuar", color = Gris, fontSize = 14.sp)
        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = cui,
            onValueChange = onCui,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("CUI") },
            placeholder = { Text("Ej. 20240001") },
            leadingIcon = { Text("🎓", fontSize = 20.sp) },
            trailingIcon = {
                if (cuiValido) {
                    // Retroalimentación positiva: el CUI tiene un formato válido.
                    Text("✓", color = Verde, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                } else if (cui.isNotEmpty()) {
                    // Control del usuario: borrar el CUI de un toque (área de 48 dp).
                    Box(
                        Modifier
                            .size(48.dp)
                            .clickable(role = Role.Button) { onCui("") }
                            .semantics { contentDescription = "Borrar CUI" },
                        contentAlignment = Alignment.Center
                    ) { Text("✕", color = Gris, fontSize = 18.sp) }
                }
            },
            supportingText = {
                Text(
                    when {
                        cui.isEmpty() -> "Tu CUI tiene 8 dígitos y está en tu carné universitario."
                        errorFormato != null -> errorFormato
                        nombreConocido != null -> "CUI reconocido: $nombreConocido. ¡Ya puedes ingresar!"
                        cuiValido -> "CUI válido. Cuéntanos tu nombre para saludarte."
                        faltan == 1 -> "Te falta 1 dígito."
                        else -> "Te faltan $faltan dígitos."
                    },
                    color = when {
                        cuiValido -> Verde
                        cui.isEmpty() -> Gris
                        else -> Rojo
                    }
                )
            },
            isError = cui.isNotEmpty() && !cuiValido,
            singleLine = true,
            enabled = !cargando,
            // Teclado numérico; la tecla ✓ ingresa directamente si ya no falta nada.
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { ingresar() }),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = if (cuiValido) Verde else Guinda,
                unfocusedBorderColor = if (cuiValido) Verde else Borde,
                focusedLabelColor = if (cuiValido) Verde else Guinda,
                cursorColor = Guinda,
                errorBorderColor = Rojo,
                errorLabelColor = Rojo
            )
        )

        // Opción 3: campo de nombre solo cuando el CUI no está registrado en la demostración.
        if (pideNombre) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = nombre,
                onValueChange = onNombre,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("¿Cómo te llamas? *") },
                placeholder = { Text("Ej. Ana") },
                leadingIcon = { Text("👋", fontSize = 20.sp) },
                supportingText = {
                    Text(
                        if (nombre.isNotEmpty() && !nombreValido) "Escribe al menos 2 letras."
                        else "Solo lo usaremos para saludarte en la app.",
                        color = if (nombre.isNotEmpty() && !nombreValido) Rojo else Gris
                    )
                },
                isError = nombre.isNotEmpty() && !nombreValido,
                singleLine = true,
                enabled = !cargando,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { ingresar() }),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Guinda, focusedLabelColor = Guinda, cursorColor = Guinda,
                    errorBorderColor = Rojo, errorLabelColor = Rojo
                )
            )
        }
        Spacer(Modifier.height(18.dp))

        Button(
            onClick = { ingresar() },
            enabled = listo && !cargando,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(15.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Guinda)
        ) {
            if (cargando) {
                // Visibilidad del estado del sistema mientras se verifica el CUI.
                CircularProgressIndicator(Modifier.size(22.dp), color = Blanco, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Verificando…", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            } else {
                Text("INGRESAR", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(18.dp))

    }
}

@Composable
private fun PantallaInicio(
    nombre: String, onPerdidos: () -> Unit, onReportar: () -> Unit,
    onMisReportes: () -> Unit, onPerfil: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(Fondo)) {
        Column(
            Modifier.fillMaxWidth().background(Guinda, RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)).padding(22.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).background(Blanco, CircleShape), contentAlignment = Alignment.Center) {
                    Text("EPIS", color = Guinda, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("UNSA EPIS", color = Blanco, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Text("Ingeniería de Sistemas", color = Color(0xFFF2DDE8), fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(23.dp))
            // Saludo neutro y personalizado (lenguaje inclusivo).
            Text(
                if (nombre.isBlank()) "¡Hola! 👋" else "¡Hola, $nombre! 💜",
                color = Blanco, fontSize = 24.sp, fontWeight = FontWeight.Bold
            )
            Text("Ayudemos a que cada objeto vuelva a su dueño.", color = Color(0xFFF7EAF1), fontSize = 14.sp)
            Spacer(Modifier.height(17.dp))
            Button(onClick = onReportar, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Blanco), shape = RoundedCornerShape(14.dp)) {
                Text("＋  Crear un reporte", color = Guinda, fontWeight = FontWeight.SemiBold)
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text("¿Qué necesitas hacer?", color = Texto, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            TarjetaAcceso("🔎", "Consultar objetos perdidos", "Revisa los objetos que otros estudiantes buscan.", onPerdidos)
            TarjetaAcceso("＋", "Reportar un objeto", "Registra algo que encontraste o que perdiste.", onReportar)
            TarjetaAcceso("📋", "Mis reportes", "Consulta tus publicaciones.", onMisReportes)
            TarjetaAcceso("👤", "Mi perfil", "Consulta tu CUI y cierra tu sesión.", onPerfil)
            Spacer(Modifier.height(12.dp))
            Text("UNSA EPIS · Comunidad que se cuida", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = Gris, fontSize = 12.sp)
        }
    }
}

@Composable
private fun TarjetaAcceso(icono: String, titulo: String, detalle: String, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Blanco),
        border = BorderStroke(1.dp, Borde)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icono, fontSize = 27.sp)
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(titulo, color = Guinda, fontWeight = FontWeight.Bold)
                Text(detalle, color = Gris, fontSize = 12.sp)
            }
            Text("›", color = Guinda, fontSize = 26.sp)
        }
    }
}

@Composable
private fun PantallaTipoReporte(onVolver: () -> Unit, onEncontrado: () -> Unit, onPerdido: () -> Unit) {
    PantallaBase("¿Qué deseas reportar?", "Selecciona el tipo de publicación", onVolver) {
        TarjetaAcceso("📦", "Encontré un objeto", "Registra un objeto que encontraste dentro de la UNSA.", onEncontrado)
        TarjetaAcceso("🔍", "Perdí un objeto", "Publica qué objeto estás buscando.", onPerdido)
    }
}

@Composable
private fun FormularioHallazgo(
    nombre: String, categoria: String, fotoUri: String?, ubicacion: String,
    descripcion: String, fecha: String, entrega: String, error: String,
    onNombre: (String) -> Unit, onCategoria: (String) -> Unit,
    onFotoSeleccionada: (String?) -> Unit,
    onUbicacion: (String) -> Unit, onDescripcion: (String) -> Unit,
    onFecha: (String) -> Unit, onEntrega: (String) -> Unit,
    onVolver: () -> Unit, onRevisar: () -> Unit
) {
    val selectorFoto = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        onFotoSeleccionada(uri?.toString())
    }

    PantallaBase("Reportar hallazgo", "Completa los datos del objeto encontrado", onVolver) {
        Text("Fotografía del objeto (opcional)", color = Guinda, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text("Puedes adjuntar una imagen para ayudar a identificarlo, pero no es obligatorio.", color = Gris, fontSize = 12.sp)
        if (fotoUri.isNullOrBlank()) {
            OutlinedButton(
                onClick = { selectorFoto.launch("image/*") },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Guinda)
            ) {
                Text("📷  Seleccionar fotografía")
            }
        } else {
            FotoPreview(fotoUri)
            OutlinedButton(
                onClick = { selectorFoto.launch("image/*") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Guinda)
            ) {
                Text("Cambiar fotografía")
            }
            TextButton(onClick = { onFotoSeleccionada(null) }) {
                Text("Quitar fotografía", color = Rojo)
            }
        }

        Text("Tipo de objeto *", color = Guinda, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        listOf(
            "Documentos",
            "Dispositivo electrónico",
            "Ropa o accesorios",
            "Llaves",
            "Accesorios personales",
            "Otro objeto"
        ).forEach { opcion ->
            OpcionRadio(opcion, categoria == opcion) { onCategoria(opcion) }
        }

        CampoTexto("Nombre del objeto *", nombre, onNombre, "Ej. Mochila negra")
        CampoTexto("¿Dónde lo encontraste? *", ubicacion, onUbicacion, "Escribe el lugar: biblioteca, aula, patio...")
        CampoTexto(
            "Descripción *", descripcion, onDescripcion,
            "Describe características que ayuden a identificarlo.",
            minLines = 3, maxLength = 250, minLength = 10
        )
        SelectorFecha("Fecha del hallazgo *", fecha, onFecha)

        Text("¿Dónde dejaste el objeto?", color = Guinda, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text("Selecciona dónde lo entregaste. Si aún lo tienes, indícalo.", color = Gris, fontSize = 12.sp)
        listOf("Aún no entregado", "Secretaría", "Vigilancia").forEach { opcion ->
            OpcionRadio(opcion, entrega == opcion) { onEntrega(opcion) }
        }
        if (error.isNotBlank()) MensajeError(error)
        BotonPrincipal("Revisar reporte →", onRevisar)
    }
}

@Composable
private fun FormularioPerdido(
    nombre: String, ubicacion: String, descripcion: String, fecha: String, error: String,
    onNombre: (String) -> Unit, onUbicacion: (String) -> Unit, onDescripcion: (String) -> Unit,
    onFecha: (String) -> Unit, onVolver: () -> Unit, onRevisar: () -> Unit
) {
    PantallaBase("Reportar objeto perdido", "Cuéntanos qué objeto estás buscando", onVolver) {
        CampoTexto("¿Qué objeto perdiste? *", nombre, onNombre, "Ej. Audífonos blancos")
        CampoTexto("¿Dónde lo perdiste? *", ubicacion, onUbicacion, "Escribe el lugar donde lo viste por última vez")
        CampoTexto(
            "Descripción *", descripcion, onDescripcion,
            "Incluye características para reconocerlo.",
            minLines = 3, maxLength = 250, minLength = 10
        )
        SelectorFecha("Fecha de pérdida *", fecha, onFecha)
        if (error.isNotBlank()) MensajeError(error)
        BotonPrincipal("Revisar reporte →", onRevisar)
    }
}

@Composable
private fun PantallaRevision(
    clase: String, nombre: String, ubicacion: String, descripcion: String, fecha: String,
    categoria: String, fotoUri: String?, entrega: String,
    onEditar: () -> Unit, onConfirmar: () -> Unit
) {
    PantallaBase("Revisar publicación", "Verifica la información antes de publicar", onEditar) {
        Card(colors = CardDefaults.cardColors(containerColor = Blanco), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(17.dp)) {
                Text(if (clase == "Encontrado") "OBJETO ENCONTRADO" else "OBJETO PERDIDO", color = Morado, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Borde)
                if (!fotoUri.isNullOrBlank()) FotoPreview(fotoUri)
                if (categoria.isNotBlank()) DatoResumen("Tipo de objeto", categoria)
                DatoResumen("Objeto", nombre)
                DatoResumen("Lugar", ubicacion)
                DatoResumen("Descripción", descripcion)
                DatoResumen("Fecha", fecha)
                if (clase == "Encontrado") DatoResumen("Entrega", entrega)
            }
        }
        BotonPrincipal("Confirmar publicación", onConfirmar)
        OutlinedButton(onClick = onEditar, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Text("Editar información", color = Guinda)
        }
    }
}

@Composable
private fun PantallaExito(reporte: ReporteUNSA?, onVerReportes: () -> Unit, onInicio: () -> Unit) {
    PantallaBase("Publicación registrada", "Tu información se agregó al prototipo", null) {
        Text("✓", color = Verde, fontSize = 58.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text("¡Reporte registrado!", color = Guinda, fontSize = 23.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Text("Código: ${reporte?.codigo.orEmpty()}", color = Morado, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        reporte?.let {
            Card(colors = CardDefaults.cardColors(containerColor = Blanco), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp)) {
                    DatoResumen("Tipo", it.clase)
                    DatoResumen("Objeto", it.nombre)
                    DatoResumen("Lugar", it.ubicacion)
                    if (it.clase == "Encontrado") DatoResumen("Entrega", it.entrega)
                }
            }
        }
        BotonPrincipal("Ver mis reportes", onVerReportes)
        OutlinedButton(onClick = onInicio, modifier = Modifier.fillMaxWidth()) { Text("Volver al inicio", color = Guinda) }
        Text("Los reportes se almacenan temporalmente en esta sesión.", color = Gris, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PantallaObjetosPerdidos(
    reportes: List<ReporteUNSA>, onVolver: () -> Unit, onReportar: () -> Unit,
    onAbrir: (ReporteUNSA) -> Unit
) {
    var busqueda by remember { mutableStateOf("") }
    val filtrados = reportes.filter {
        it.nombre.contains(busqueda, ignoreCase = true) || it.ubicacion.contains(busqueda, ignoreCase = true)
    }
    PantallaBase("Objetos perdidos", "Publicaciones de estudiantes", onVolver) {
        CampoTexto("Buscar objeto o lugar", busqueda, { busqueda = it }, "Ej. audífonos, biblioteca")
        if (filtrados.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = Blanco), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔎", fontSize = 38.sp)
                    Text(if (busqueda.isBlank()) "Aún no hay objetos perdidos publicados" else "No encontramos resultados", color = Guinda, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text("Puedes registrar un objeto perdido para que aparezca aquí.", color = Gris, fontSize = 12.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    BotonPrincipal("Reportar objeto perdido", onReportar)
                }
            }
        } else {
            filtrados.forEach { r -> TarjetaReporte(r, onClick = { onAbrir(r) }) }
        }
        Text("Solo se muestran publicaciones registradas en este dispositivo durante esta sesión.", color = Gris, fontSize = 11.sp)
    }
}

@Composable
private fun PantallaMisReportes(
    reportes: List<ReporteUNSA>, onVolver: () -> Unit, onNuevo: () -> Unit,
    onAbrir: (ReporteUNSA) -> Unit
) {
    var filtro by remember { mutableStateOf("Todos") }
    PantallaBase("Mis reportes", "Historial de tus publicaciones", onVolver) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Todos", "Perdidos", "Encontrados").forEach { opcion ->
                FilterChip(selected = filtro == opcion, onClick = { filtro = opcion }, label = { Text(opcion) })
            }
        }
        val lista = when (filtro) {
            "Perdidos" -> reportes.filter { it.clase == "Perdido" }
            "Encontrados" -> reportes.filter { it.clase == "Encontrado" }
            else -> reportes
        }
        if (lista.isEmpty()) {
            Text("Todavía no tienes publicaciones en esta categoría.", color = Gris)
        } else {
            lista.forEach { r -> TarjetaReporte(r, onClick = { onAbrir(r) }) }
        }
        BotonPrincipal("＋ Nuevo reporte", onNuevo)
    }
}

@Composable
private fun TarjetaReporte(reporte: ReporteUNSA, onClick: () -> Unit) {
    // Tarjeta resumida (lista): al tocarla se abre el detalle.
    Card(
        Modifier.fillMaxWidth().clickable(onClickLabel = "Ver detalle", onClick = onClick),
        shape = RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = Blanco),
        border = BorderStroke(1.dp, Borde)
    ) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (reporte.clase == "Perdido") "🔎" else "📦", fontSize = 25.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(reporte.nombre, color = Guinda, fontWeight = FontWeight.Bold)
                    Text("${reporte.clase} · ${reporte.codigo}", color = Morado, fontSize = 11.sp)
                }
                Surface(color = Lila, shape = RoundedCornerShape(20.dp)) {
                    Text(reporte.estado, color = Guinda, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("Lugar: ${reporte.ubicacion} · ${reporte.fecha}", color = Gris, fontSize = 13.sp)
            Text("Ver detalle ›", color = Guinda, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.End))
        }
    }
}

@Composable
private fun PantallaDetalle(reporte: ReporteUNSA?, onVolver: () -> Unit) {
    PantallaBase("Detalle del reporte", reporte?.codigo ?: "Reporte no disponible", onVolver) {
        if (reporte == null) {
            MensajeError("No se encontró el reporte. Vuelve a la lista e inténtalo de nuevo.")
        } else Card(colors = CardDefaults.cardColors(containerColor = Blanco), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(17.dp)) {
                Text(if (reporte.clase == "Encontrado") "OBJETO ENCONTRADO" else "OBJETO PERDIDO", color = Morado, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Borde)
                if (reporte.fotoUri != null) FotoPreview(reporte.fotoUri)
                if (reporte.categoria.isNotBlank()) DatoResumen("Tipo de objeto", reporte.categoria)
                DatoResumen("Objeto", reporte.nombre)
                DatoResumen("Lugar", reporte.ubicacion)
                DatoResumen("Fecha", reporte.fecha)
                DatoResumen("Descripción", reporte.descripcion)
                if (reporte.clase == "Encontrado") DatoResumen("Dónde recogerlo", reporte.entrega)
                DatoResumen("Estado", reporte.estado)
            }
        }
        OutlinedButton(onClick = onVolver, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) {
            Text("Volver a la lista", color = Guinda)
        }
    }
}

@Composable
private fun PantallaPerfil(nombre: String, cui: String, onVolver: () -> Unit, onCerrarSesion: () -> Unit) {
    // PATRÓN CONFIRMACIÓN DE ACCIONES: se pide confirmar antes de cerrar sesión.
    var confirmar by rememberSaveable { mutableStateOf(false) }
    if (confirmar) {
        AlertDialog(
            onDismissRequest = { confirmar = false },
            title = { Text("¿Cerrar sesión?") },
            text = { Text("Tendrás que ingresar tu CUI de nuevo. Tus reportes de esta sesión se conservan en el dispositivo.") },
            confirmButton = {
                TextButton(onClick = { confirmar = false; onCerrarSesion() }) { Text("Cerrar sesión", color = Rojo) }
            },
            dismissButton = {
                TextButton(onClick = { confirmar = false }) { Text("Cancelar", color = Guinda) }
            }
        )
    }
    PantallaBase("Mi perfil", "Información de la sesión", onVolver) {
        Card(colors = CardDefaults.cardColors(containerColor = Blanco), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(68.dp).background(Lila, CircleShape), contentAlignment = Alignment.Center) {
                    Text("🎓", fontSize = 34.sp)
                }
                Spacer(Modifier.height(12.dp))
                Text(nombre.ifBlank { "Estudiante UNSA" }, color = Guinda, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Estudiante UNSA", color = Gris)
                Spacer(Modifier.height(12.dp))
                DatoResumen("CUI", cui)
                DatoResumen("Escuela", "Ingeniería de Sistemas · EPIS")
            }
        }
        BotonPrincipal("Cerrar sesión", { confirmar = true })
    }
}

@Composable
private fun PantallaBase(titulo: String, subtitulo: String, onVolver: (() -> Unit)?, contenido: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(Fondo)) {
        Row(
            Modifier.fillMaxWidth().background(Guinda).padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onVolver != null) {
                // Botón Volver accesible: área táctil de 48dp y descripción para TalkBack.
                Box(
                    Modifier
                        .size(48.dp)
                        .clickable(role = Role.Button) { onVolver() }
                        .semantics { contentDescription = "Volver" },
                    contentAlignment = Alignment.Center
                ) {
                    Text("‹", color = Blanco, fontSize = 36.sp)
                }
                Spacer(Modifier.width(4.dp))
            }
            Column {
                Text(titulo, color = Blanco, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(subtitulo, color = Color(0xFFF2DDE8), fontSize = 12.sp)
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = contenido
        )
    }
}

@Composable
private fun SelectorFecha(
    etiqueta: String,
    fecha: String,
    onFecha: (String) -> Unit
) {
    val context = LocalContext.current
    val hoy = Calendar.getInstance()

    OutlinedButton(
        onClick = {
            val dialogo = DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    val fechaSeleccionada = "%02d/%02d/%04d".format(dayOfMonth, month + 1, year)
                    onFecha(fechaSeleccionada)
                },
                hoy.get(Calendar.YEAR),
                hoy.get(Calendar.MONTH),
                hoy.get(Calendar.DAY_OF_MONTH)
            )
            // Impide seleccionar fechas futuras.
            dialogo.datePicker.maxDate = System.currentTimeMillis()
            dialogo.show()
        },
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Borde),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (fecha.isBlank()) Gris else Texto)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(etiqueta, color = Gris, fontSize = 11.sp)
                Text(
                    if (fecha.isBlank()) "Seleccionar fecha" else fecha,
                    color = if (fecha.isBlank()) Gris else Texto,
                    fontSize = 15.sp,
                    fontWeight = if (fecha.isBlank()) FontWeight.Normal else FontWeight.SemiBold
                )
            }
            Text("📅", fontSize = 20.sp)
        }
    }
}

@Composable
private fun CampoTexto(
    etiqueta: String, valor: String, onValor: (String) -> Unit, ejemplo: String,
    minLines: Int = 1,
    maxLength: Int? = null,
    minLength: Int? = null
) {
    Column {
        OutlinedTextField(
            value = valor,
            onValueChange = { nuevoTexto ->
                onValor(if (maxLength != null) nuevoTexto.take(maxLength) else nuevoTexto)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(etiqueta) },
            placeholder = { Text(ejemplo) },
            minLines = minLines,
            singleLine = minLines == 1,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Guinda,
                focusedLabelColor = Guinda,
                cursorColor = Guinda
            )
        )

        if (maxLength != null && minLength != null) {
            Text(
                text = "${valor.length}/$maxLength caracteres · Mínimo: $minLength",
                color = if (valor.isNotEmpty() && valor.length < minLength) Rojo else Gris,
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.End).padding(top = 4.dp)
            )
            if (valor.isNotEmpty() && valor.length < minLength) {
                Text(
                    "La descripción es demasiado corta. Escribe al menos $minLength caracteres.",
                    color = Rojo,
                    fontSize = 11.sp
                )
            } else if (valor.length == maxLength) {
                Text(
                    "Has alcanzado el límite máximo de $maxLength caracteres.",
                    color = Rojo,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun FotoPreview(uriString: String) {
    AndroidView(
        factory = { context ->
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                adjustViewBounds = true
                contentDescription = "Fotografía del objeto reportado"
            }
        },
        update = { imageView ->
            imageView.setImageURI(Uri.parse(uriString))
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp)
            .background(Lila, RoundedCornerShape(14.dp))
    )
}

@Composable
private fun OpcionRadio(texto: String, seleccionado: Boolean, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(13.dp),
        colors = CardDefaults.cardColors(containerColor = if (seleccionado) Lila else Blanco),
        border = BorderStroke(1.dp, if (seleccionado) Morado else Borde)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = seleccionado, onClick = onClick, colors = RadioButtonDefaults.colors(selectedColor = Guinda))
            Text(texto, color = Texto)
        }
    }
}

@Composable
private fun DatoResumen(etiqueta: String, valor: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(etiqueta, color = Gris, fontSize = 12.sp)
        Text(valor, color = Texto, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MensajeError(texto: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE9E9)), shape = RoundedCornerShape(12.dp)) {
        Text("⚠ $texto", color = Rojo, modifier = Modifier.padding(13.dp), fontSize = 13.sp)
    }
}

@Composable
private fun BotonPrincipal(texto: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.buttonColors(containerColor = Guinda)
    ) {
        Text(texto, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}