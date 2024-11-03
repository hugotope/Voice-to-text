# Instrucciones para Subir a GitHub

## ✅ Repositorio Local Preparado

El repositorio local ya está inicializado y con el commit inicial realizado.

## 📋 Pasos para Crear el Repositorio en GitHub

### Opción 1: Usando la Web de GitHub (Recomendado)

1. **Ve a GitHub**: https://github.com/new
2. **Configura el repositorio**:
   - **Repository name**: `Voice-to-text`
   - **Description**: "Sistema de transcripción de audio a texto con Whisper AI y generación de informes estructurados"
   - **Visibility**: ✅ **Private** (marcar como privado)
   - **NO marques** "Initialize this repository with a README" (ya tenemos archivos)
3. **Clic en "Create repository"**

4. **Copia la URL del repositorio** (será algo como: `https://github.com/TU_USUARIO/Voice-to-text.git`)

5. **Ejecuta estos comandos en tu terminal**:

```bash
cd C:\Users\D3T\Desktop\Salle\ProyectoICE

# Agregar el remote de GitHub (reemplaza TU_USUARIO con tu usuario de GitHub)
git remote add origin https://github.com/TU_USUARIO/Voice-to-text.git

# Cambiar a branch main (si estás en master)
git branch -M main

# Subir el código
git push -u origin main
```

### Opción 2: Usando GitHub CLI (gh)

Si tienes GitHub CLI instalado:

```bash
cd C:\Users\D3T\Desktop\Salle\ProyectoICE

# Crear repositorio privado
gh repo create Voice-to-text --private --source=. --remote=origin --push
```

### Opción 3: Usando SSH (si tienes SSH configurado)

```bash
cd C:\Users\D3T\Desktop\Salle\ProyectoICE

# Crear repositorio en GitHub primero (usando la web)
# Luego:
git remote add origin git@github.com:TU_USUARIO/Voice-to-text.git
git branch -M main
git push -u origin main
```

## 🔐 Autenticación

Si te pide autenticación:
- **Usuario**: Tu usuario de GitHub
- **Contraseña**: Usa un **Personal Access Token** (no tu contraseña)
  - Crea uno en: https://github.com/settings/tokens
  - Permisos necesarios: `repo` (acceso completo a repositorios privados)

## ✅ Verificación

Después de subir, verifica en:
- https://github.com/TU_USUARIO/Voice-to-text

Deberías ver todos los archivos del proyecto.

## 📝 Notas

- El repositorio está configurado como **privado**
- Los archivos sensibles (`.env`, `uploads/`, `venv/`) están en `.gitignore`
- El README.md ya está incluido con documentación del proyecto
