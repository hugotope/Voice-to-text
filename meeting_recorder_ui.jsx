import { useState, useEffect, useRef } from "react";

const LANGUAGES = [
  { code: "es", label: "Español", flag: "🇪🇸" },
  { code: "en", label: "English", flag: "🇬🇧" },
  { code: "fr", label: "Français", flag: "🇫🇷" },
  { code: "de", label: "Deutsch", flag: "🇩🇪" },
  { code: "pt", label: "Português", flag: "🇧🇷" },
  { code: "it", label: "Italiano", flag: "🇮🇹" },
  { code: "zh", label: "中文", flag: "🇨🇳" },
  { code: "ja", label: "日本語", flag: "🇯🇵" },
];

const SK = {
  groups: "voicelog_groups",
  trash: "voicelog_trash",
  serverUrl: "voicelog_server_url",
  lang: "voicelog_lang",
};

const DEFAULT_SERVER = "http://localhost:3000";

function loadLS(key, fallback) {
  try {
    const v = localStorage.getItem(key);
    return v ? JSON.parse(v) : fallback;
  } catch {
    return fallback;
  }
}

export default function App() {
  const [screen, setScreen] = useState("home");
  const [selectedLang, setSelectedLang] = useState(() => loadLS(SK.lang, "es"));
  const [memberCount, setMemberCount] = useState(2);
  const [groups, setGroups] = useState(() => loadLS(SK.groups, []));
  const [trashedGroups, setTrashedGroups] = useState(() => loadLS(SK.trash, []));
  const [activeGroupId, setActiveGroupId] = useState(null);
  const [showLangPicker, setShowLangPicker] = useState(false);
  const [showNewGroupModal, setShowNewGroupModal] = useState(false);
  const [showSettingsModal, setShowSettingsModal] = useState(false);
  const [showTrashConfirm, setShowTrashConfirm] = useState(null);
  const [newGroupName, setNewGroupName] = useState("");
  const [newGroupMembers, setNewGroupMembers] = useState([""]);
  const [isRecording, setIsRecording] = useState(false);
  const [isProcessing, setIsProcessing] = useState(false);
  const [recordingTime, setRecordingTime] = useState(0);
  const [playingId, setPlayingId] = useState(null);
  const [isAudioPaused, setIsAudioPaused] = useState(false);
  const [serverUrl, setServerUrl] = useState(() => loadLS(SK.serverUrl, DEFAULT_SERVER) || DEFAULT_SERVER);
  const [serverUrlDraft, setServerUrlDraft] = useState(() => loadLS(SK.serverUrl, DEFAULT_SERVER) || DEFAULT_SERVER);
  const [processError, setProcessError] = useState(null);

  const mediaRecorderRef = useRef(null);
  const chunksRef = useRef([]);
  const timerRef = useRef(null);
  const audioRef = useRef(null);

  const activeGroup = groups.find((g) => g.id === activeGroupId) || null;
  const currentLang = LANGUAGES.find((l) => l.code === selectedLang);

  // Persist to localStorage
  useEffect(() => {
    try { localStorage.setItem(SK.groups, JSON.stringify(groups)); } catch {}
  }, [groups]);

  useEffect(() => {
    try { localStorage.setItem(SK.trash, JSON.stringify(trashedGroups)); } catch {}
  }, [trashedGroups]);

  useEffect(() => {
    try { localStorage.setItem(SK.lang, selectedLang); } catch {}
  }, [selectedLang]);

  // Recording timer
  useEffect(() => {
    if (isRecording) {
      timerRef.current = setInterval(() => setRecordingTime((t) => t + 1), 1000);
    } else {
      clearInterval(timerRef.current);
    }
    return () => clearInterval(timerRef.current);
  }, [isRecording]);

  const fmt = (s) =>
    `${Math.floor(s / 60).toString().padStart(2, "0")}:${(s % 60).toString().padStart(2, "0")}`;

  // ── RECORDING ──────────────────────────────────────────────────────────────

  const startRecording = async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const mimeType = MediaRecorder.isTypeSupported("audio/webm;codecs=opus")
        ? "audio/webm;codecs=opus"
        : MediaRecorder.isTypeSupported("audio/webm")
        ? "audio/webm"
        : "audio/mp4";
      const mr = new MediaRecorder(stream, { mimeType });
      chunksRef.current = [];
      mr.ondataavailable = (e) => {
        if (e.data.size > 0) chunksRef.current.push(e.data);
      };
      mr.start(1000);
      mediaRecorderRef.current = mr;
      setRecordingTime(0);
      setIsRecording(true);
      setProcessError(null);
    } catch (e) {
      alert("No se pudo acceder al micrófono: " + e.message);
    }
  };

  const stopAndProcess = async () => {
    const mr = mediaRecorderRef.current;
    if (!mr) return;

    const finalDuration = recordingTime;
    setIsRecording(false);
    setIsProcessing(true);
    setProcessError(null);

    await new Promise((resolve) => {
      mr.onstop = resolve;
      mr.stop();
      mr.stream.getTracks().forEach((t) => t.stop());
    });

    try {
      const mimeType = chunksRef.current[0]?.type || "audio/webm";
      const ext = mimeType.includes("mp4") ? "mp4" : "webm";
      const blob = new Blob(chunksRef.current, { type: mimeType });
      const filename = `meeting_audio_${Date.now()}.${ext}`;

      const fd = new FormData();
      fd.append("audio", blob, filename);
      fd.append("language", selectedLang);

      const res = await fetch(`${serverUrl}/process`, { method: "POST", body: fd });
      if (!res.ok) {
        const txt = await res.text();
        throw new Error(txt || res.statusText);
      }
      const data = await res.json();

      const now = new Date();
      const rec = {
        id: Date.now(),
        title: `Grabación ${now.toLocaleDateString("es-ES")} ${now.toLocaleTimeString("es-ES", { hour: "2-digit", minute: "2-digit" })}`,
        date: now.toISOString().split("T")[0],
        duration: fmt(finalDuration),
        lang: selectedLang,
        audioFile: data.audioFile || null,
        transcription: data.transcription || null,
        pdfFile: data.pdfFile || null,
      };

      setGroups((prev) =>
        prev.map((g) =>
          g.id === activeGroupId ? { ...g, recordings: [rec, ...g.recordings] } : g
        )
      );
    } catch (e) {
      setProcessError("Error al procesar: " + e.message);
    } finally {
      setIsProcessing(false);
    }
  };

  const toggleRecording = () => {
    if (isRecording) stopAndProcess();
    else startRecording();
  };

  // ── AUDIO PLAYBACK ─────────────────────────────────────────────────────────

  const playAudio = (rec) => {
    if (!rec.audioFile) return;
    const url = `${serverUrl}/files/audio/${rec.audioFile}`;

    if (playingId === rec.id) {
      if (audioRef.current?.paused) {
        audioRef.current.play();
        setIsAudioPaused(false);
      } else {
        audioRef.current?.pause();
        setIsAudioPaused(true);
      }
    } else {
      audioRef.current?.pause();
      const audio = new Audio(url);
      audio.play().catch(() => {
        setPlayingId(null);
        setIsAudioPaused(false);
        alert("No se puede reproducir el audio. Verifica que el servidor esté activo.");
      });
      audio.onended = () => {
        setPlayingId(null);
        setIsAudioPaused(false);
      };
      audioRef.current = audio;
      setPlayingId(rec.id);
      setIsAudioPaused(false);
    }
  };

  const stopAudio = () => {
    audioRef.current?.pause();
    audioRef.current = null;
    setPlayingId(null);
    setIsAudioPaused(false);
  };

  // ── GROUPS ─────────────────────────────────────────────────────────────────

  const addNewGroup = () => {
    const members = newGroupMembers.filter((m) => m.trim());
    if (!newGroupName.trim() || members.length === 0) return;
    const group = {
      id: Date.now(),
      name: newGroupName.trim(),
      type: "group",
      members,
      recordings: [],
    };
    setGroups((prev) => [group, ...prev]);
    setShowNewGroupModal(false);
    setNewGroupName("");
    setNewGroupMembers([""]);
    setActiveGroupId(group.id);
    setScreen("group-detail");
  };

  const updateMember = (i, val) => {
    const updated = [...newGroupMembers];
    updated[i] = val;
    setNewGroupMembers(updated);
  };

  const addMember = () => setNewGroupMembers((m) => [...m, ""]);

  const removeMember = (i) => {
    const updated = newGroupMembers.filter((_, idx) => idx !== i);
    setNewGroupMembers(updated.length ? updated : [""]);
  };

  // ── TRASH ──────────────────────────────────────────────────────────────────

  const moveToTrash = (groupId, e) => {
    if (e) e.stopPropagation();
    const group = groups.find((g) => g.id === groupId);
    if (!group) return;
    setTrashedGroups((prev) => [{ ...group, trashedAt: new Date().toISOString() }, ...prev]);
    setGroups((prev) => prev.filter((g) => g.id !== groupId));
    if (screen === "group-detail" && activeGroupId === groupId) {
      setScreen("home");
    }
    stopAudio();
  };

  const restoreFromTrash = (groupId) => {
    const group = trashedGroups.find((g) => g.id === groupId);
    if (!group) return;
    const { trashedAt, ...restored } = group;
    setGroups((prev) => [restored, ...prev]);
    setTrashedGroups((prev) => prev.filter((g) => g.id !== groupId));
  };

  const deleteForever = (groupId) => {
    setShowTrashConfirm(null);
    setTrashedGroups((prev) => prev.filter((g) => g.id !== groupId));
  };

  const emptyTrash = () => {
    setShowTrashConfirm("all");
  };

  const saveServerUrl = () => {
    const url = serverUrlDraft.trim().replace(/\/$/, "");
    setServerUrl(url);
    try { localStorage.setItem(SK.serverUrl, JSON.stringify(url)); } catch {}
    setShowSettingsModal(false);
    stopAudio();
  };

  // ── RENDER ─────────────────────────────────────────────────────────────────

  return (
    <div style={styles.root}>
      <div style={styles.noiseBg} />

      {/* ─── HOME ──────────────────────────────────────────── */}
      {screen === "home" && (
        <div style={styles.page}>
          <div style={styles.header}>
            <div>
              <div style={styles.appTag}>RECORDER</div>
              <h1 style={styles.appTitle}>VoiceLog</h1>
            </div>
            <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
              <button style={styles.iconBtn} onClick={() => setShowSettingsModal(true)} title="Configuración servidor">
                ⚙
              </button>
              <button
                style={{ ...styles.iconBtn, position: "relative" }}
                onClick={() => setScreen("trash")}
                title="Papelera"
              >
                🗑
                {trashedGroups.length > 0 && (
                  <span style={styles.trashBadge}>{trashedGroups.length}</span>
                )}
              </button>
              <button style={styles.langBtn} onClick={() => setShowLangPicker(!showLangPicker)}>
                <span style={{ fontSize: 20 }}>{currentLang.flag}</span>
                <span style={styles.langCode}>{currentLang.code.toUpperCase()}</span>
                <span style={styles.chevron}>▾</span>
              </button>
            </div>
          </div>

          {showLangPicker && (
            <div style={styles.langDropdown}>
              {LANGUAGES.map((l) => (
                <button
                  key={l.code}
                  style={{ ...styles.langOption, ...(l.code === selectedLang ? styles.langOptionActive : {}) }}
                  onClick={() => { setSelectedLang(l.code); setShowLangPicker(false); }}
                >
                  <span>{l.flag}</span>
                  <span style={{ fontFamily: "'DM Mono', monospace", fontSize: 13 }}>{l.label}</span>
                </button>
              ))}
            </div>
          )}

          <div style={styles.section}>
            <div style={styles.sectionLabel}>INTEGRANTES POR DEFECTO</div>
            <div style={styles.memberRow}>
              <button style={styles.counterBtn} onClick={() => setMemberCount(Math.max(1, memberCount - 1))}>−</button>
              <div style={styles.memberDisplay}>
                <span style={styles.memberNum}>{memberCount}</span>
                <span style={styles.memberSub}>persona{memberCount !== 1 ? "s" : ""}</span>
              </div>
              <button style={styles.counterBtn} onClick={() => setMemberCount(Math.min(20, memberCount + 1))}>+</button>
            </div>
            <div style={styles.memberDots}>
              {Array.from({ length: Math.min(memberCount, 12) }).map((_, i) => (
                <div key={i} style={styles.dot} />
              ))}
              {memberCount > 12 && <span style={styles.moreText}>+{memberCount - 12}</span>}
            </div>
          </div>

          <div style={styles.section}>
            <div style={styles.sectionRow}>
              <div style={styles.sectionLabel}>GRUPOS ({groups.length})</div>
              <button style={styles.newBtn} onClick={() => setShowNewGroupModal(true)}>+ NUEVO</button>
            </div>
            {groups.length === 0 ? (
              <div style={styles.emptyState}>
                Sin grupos aún.{"\n"}Crea uno con + NUEVO
              </div>
            ) : (
              <div style={styles.groupList}>
                {groups.map((g) => (
                  <button
                    key={g.id}
                    style={styles.groupCard}
                    onClick={() => { setActiveGroupId(g.id); setScreen("group-detail"); }}
                  >
                    <div style={styles.groupTypeBadge}>👥 Grupo</div>
                    <div style={styles.groupName}>{g.name}</div>
                    <div style={styles.groupMeta}>
                      <span>👤 {g.members.length} miembros</span>
                      <span>🎙 {g.recordings.length} grabaciones</span>
                    </div>
                    <button
                      style={styles.trashCardBtn}
                      onClick={(e) => moveToTrash(g.id, e)}
                      title="Mover a papelera"
                    >
                      🗑
                    </button>
                  </button>
                ))}
              </div>
            )}
          </div>
        </div>
      )}

      {/* ─── GROUP DETAIL ───────────────────────────────────── */}
      {screen === "group-detail" && activeGroup && (
        <div style={styles.page}>
          <div style={styles.header}>
            <button style={styles.backBtn} onClick={() => { setScreen("home"); stopAudio(); }}>
              ← Volver
            </button>
            <button
              style={{ ...styles.iconBtn, color: "#f87171" }}
              onClick={(e) => moveToTrash(activeGroup.id, e)}
              title="Mover a papelera"
            >
              🗑
            </button>
          </div>

          <div style={styles.groupTypeBadge}>👥 Grupo</div>
          <h2 style={styles.groupDetailTitle}>{activeGroup.name}</h2>

          <div style={styles.section}>
            <div style={styles.sectionLabel}>INTEGRANTES</div>
            <div style={styles.memberChips}>
              {activeGroup.members.map((m, i) => (
                <div key={i} style={styles.memberChip}>
                  <div style={styles.avatar}>{m[0].toUpperCase()}</div>
                  <span style={styles.memberChipName}>{m}</span>
                </div>
              ))}
            </div>
          </div>

          <div style={styles.section}>
            <div style={styles.sectionLabel}>NUEVA GRABACIÓN</div>
            <button
              style={{ ...styles.recordButton, ...(isRecording ? styles.recordButtonActive : {}), ...(isProcessing ? styles.recordButtonProcessing : {}) }}
              onClick={toggleRecording}
              disabled={isProcessing}
            >
              <div style={styles.recordDot} />
              <span style={styles.recordLabel}>
                {isProcessing ? "PROCESANDO..." : isRecording ? "DETENER GRABACIÓN" : "INICIAR GRABACIÓN"}
              </span>
            </button>
            {isRecording && (
              <div style={styles.recordingIndicator}>
                <div style={styles.recordPulse} />
                <span style={styles.recordingText}>
                  Grabando {currentLang.flag} {currentLang.label} · {fmt(recordingTime)}
                </span>
              </div>
            )}
            {isProcessing && (
              <div style={styles.processingBar}>
                <div style={styles.processingText}>⏳ Transcribiendo con Whisper...</div>
              </div>
            )}
            {processError && (
              <div style={styles.errorBox}>{processError}</div>
            )}
          </div>

          <div style={styles.section}>
            <div style={styles.sectionLabel}>GRABACIONES ({activeGroup.recordings.length})</div>
            {activeGroup.recordings.length === 0 ? (
              <div style={styles.emptyState}>Sin grabaciones aún</div>
            ) : (
              <div style={styles.recordingList}>
                {activeGroup.recordings.map((r) => {
                  const isPlaying = playingId === r.id;
                  const canPlay = !!r.audioFile;
                  return (
                    <div key={r.id} style={styles.recordingCard}>
                      <div style={styles.recIconWrap}>
                        <span style={{ fontSize: 18 }}>🎙</span>
                      </div>
                      <div style={styles.recInfo}>
                        <div style={styles.recTitle}>{r.title}</div>
                        <div style={styles.recMeta}>
                          <span>{r.date}</span>
                          <span>⏱ {r.duration}</span>
                          <span>{LANGUAGES.find((l) => l.code === r.lang)?.flag} {r.lang.toUpperCase()}</span>
                        </div>
                        {r.transcription && (
                          <div style={styles.recTranscription}>
                            {r.transcription.length > 120
                              ? r.transcription.slice(0, 120) + "…"
                              : r.transcription}
                          </div>
                        )}
                        {r.pdfFile && (
                          <a
                            href={`${serverUrl}/files/report/${r.pdfFile}`}
                            target="_blank"
                            rel="noreferrer"
                            style={styles.pdfLink}
                            onClick={(e) => e.stopPropagation()}
                          >
                            📄 Ver informe PDF
                          </a>
                        )}
                      </div>
                      <button
                        style={{
                          ...styles.playBtn,
                          ...(isPlaying && !isAudioPaused ? styles.playBtnActive : {}),
                          ...(canPlay ? {} : styles.playBtnDisabled),
                        }}
                        onClick={() => playAudio(r)}
                        disabled={!canPlay}
                        title={canPlay ? (isPlaying && !isAudioPaused ? "Pausar" : "Reproducir") : "Sin audio"}
                      >
                        {isPlaying && !isAudioPaused ? "⏸" : "▶"}
                      </button>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </div>
      )}

      {/* ─── TRASH ─────────────────────────────────────────── */}
      {screen === "trash" && (
        <div style={styles.page}>
          <div style={styles.header}>
            <button style={styles.backBtn} onClick={() => setScreen("home")}>← Volver</button>
            <div style={styles.appTag} >PAPELERA</div>
          </div>

          <h2 style={{ ...styles.groupDetailTitle, color: "#f87171" }}>🗑 Papelera</h2>

          {trashedGroups.length === 0 ? (
            <div style={styles.emptyState}>La papelera está vacía</div>
          ) : (
            <>
              <button style={styles.emptyTrashBtn} onClick={emptyTrash}>
                Vaciar papelera ({trashedGroups.length})
              </button>
              <div style={styles.groupList}>
                {trashedGroups.map((g) => (
                  <div key={g.id} style={{ ...styles.groupCard, cursor: "default" }}>
                    <div style={{ ...styles.groupTypeBadge, opacity: 0.5 }}>👥 Grupo</div>
                    <div style={styles.groupName}>{g.name}</div>
                    <div style={styles.groupMeta}>
                      <span>👤 {g.members.length} miembros</span>
                      <span>🎙 {g.recordings.length} grabaciones</span>
                    </div>
                    {g.trashedAt && (
                      <div style={{ ...styles.groupMeta, marginTop: 4 }}>
                        <span>Eliminado: {new Date(g.trashedAt).toLocaleDateString("es-ES")}</span>
                      </div>
                    )}
                    <div style={styles.trashActions}>
                      <button style={styles.restoreBtn} onClick={() => restoreFromTrash(g.id)}>
                        ↩ Restaurar
                      </button>
                      <button style={styles.deleteBtn} onClick={() => setShowTrashConfirm(g.id)}>
                        🗑 Borrar definitivamente
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            </>
          )}
        </div>
      )}

      {/* ─── MODAL: NUEVO GRUPO ────────────────────────────── */}
      {showNewGroupModal && (
        <div style={styles.modalOverlay} onClick={() => setShowNewGroupModal(false)}>
          <div style={styles.modal} onClick={(e) => e.stopPropagation()}>
            <div style={styles.modalTitle}>NUEVO GRUPO</div>

            <div style={styles.formGroup}>
              <label style={styles.formLabel}>NOMBRE DEL GRUPO</label>
              <input
                style={styles.formInput}
                placeholder="ej. Equipo de Diseño..."
                value={newGroupName}
                onChange={(e) => setNewGroupName(e.target.value)}
                autoFocus
              />
            </div>

            <div style={styles.formGroup}>
              <label style={styles.formLabel}>INTEGRANTES</label>
              {newGroupMembers.map((m, i) => (
                <div key={i} style={styles.memberInputRow}>
                  <input
                    style={{ ...styles.formInput, flex: 1 }}
                    placeholder={`Integrante ${i + 1}`}
                    value={m}
                    onChange={(e) => updateMember(i, e.target.value)}
                  />
                  <button style={styles.removeMemberBtn} onClick={() => removeMember(i)}>✕</button>
                </div>
              ))}
              <button style={styles.addMemberBtn} onClick={addMember}>+ Añadir integrante</button>
            </div>

            <div style={styles.modalActions}>
              <button style={styles.cancelBtn} onClick={() => setShowNewGroupModal(false)}>Cancelar</button>
              <button style={styles.createBtn} onClick={addNewGroup}>Crear →</button>
            </div>
          </div>
        </div>
      )}

      {/* ─── MODAL: CONFIGURACIÓN ──────────────────────────── */}
      {showSettingsModal && (
        <div style={styles.modalOverlay} onClick={() => setShowSettingsModal(false)}>
          <div style={styles.modal} onClick={(e) => e.stopPropagation()}>
            <div style={styles.modalTitle}>⚙ CONFIGURACIÓN</div>

            <div style={styles.formGroup}>
              <label style={styles.formLabel}>URL DEL SERVIDOR</label>
              <input
                style={styles.formInput}
                placeholder="http://192.168.1.X:3000"
                value={serverUrlDraft}
                onChange={(e) => setServerUrlDraft(e.target.value)}
              />
              <div style={{ fontSize: 10, color: "#444", marginTop: 4, fontFamily: "'DM Mono', monospace" }}>
                Si usas la app en el móvil, pon la IP local del ordenador donde corre el servidor.
              </div>
            </div>

            <div style={styles.modalActions}>
              <button style={styles.cancelBtn} onClick={() => setShowSettingsModal(false)}>Cancelar</button>
              <button style={styles.createBtn} onClick={saveServerUrl}>Guardar →</button>
            </div>
          </div>
        </div>
      )}

      {/* ─── MODAL: CONFIRMAR BORRADO ──────────────────────── */}
      {showTrashConfirm && (
        <div style={styles.modalOverlay} onClick={() => setShowTrashConfirm(null)}>
          <div style={{ ...styles.modal, maxHeight: "auto" }} onClick={(e) => e.stopPropagation()}>
            <div style={styles.modalTitle}>⚠ CONFIRMAR BORRADO</div>
            <p style={{ color: "#aaa", fontSize: 13, marginBottom: 20, fontFamily: "'DM Mono', monospace", lineHeight: 1.6 }}>
              {showTrashConfirm === "all"
                ? "¿Borrar definitivamente todos los elementos de la papelera? Esta acción no se puede deshacer."
                : "¿Borrar definitivamente este grupo? Esta acción no se puede deshacer."}
            </p>
            <div style={styles.modalActions}>
              <button style={styles.cancelBtn} onClick={() => setShowTrashConfirm(null)}>Cancelar</button>
              <button
                style={{ ...styles.createBtn, background: "#1f0d0d", borderColor: "#3d1a1a", color: "#f87171" }}
                onClick={() => {
                  if (showTrashConfirm === "all") {
                    setTrashedGroups([]);
                    setShowTrashConfirm(null);
                  } else {
                    deleteForever(showTrashConfirm);
                  }
                }}
              >
                Borrar definitivamente
              </button>
            </div>
          </div>
        </div>
      )}

      <style>{`
        @import url('https://fonts.googleapis.com/css2?family=Space+Mono:wght@400;700&family=DM+Mono:wght@300;400;500&family=Syne:wght@400;700;800&display=swap');
        * { box-sizing: border-box; margin: 0; padding: 0; }
        ::-webkit-scrollbar { width: 4px; }
        ::-webkit-scrollbar-track { background: transparent; }
        ::-webkit-scrollbar-thumb { background: #333; border-radius: 2px; }
        button:disabled { opacity: 0.5; cursor: not-allowed; }
        @keyframes pulse {
          0%, 100% { opacity: 1; transform: scale(1); }
          50% { opacity: 0.4; transform: scale(1.4); }
        }
        @keyframes fadeIn {
          from { opacity: 0; transform: translateY(8px); }
          to { opacity: 1; transform: translateY(0); }
        }
        @keyframes processingSlide {
          0% { transform: translateX(-100%); }
          100% { transform: translateX(100%); }
        }
      `}</style>
    </div>
  );
}

const styles = {
  root: {
    minHeight: "100vh",
    background: "#0a0a0b",
    color: "#e8e6e1",
    fontFamily: "'DM Mono', monospace",
    position: "relative",
    overflowX: "hidden",
  },
  noiseBg: {
    position: "fixed",
    inset: 0,
    backgroundImage: `url("data:image/svg+xml,%3Csvg viewBox='0 0 200 200' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='4' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)' opacity='0.04'/%3E%3C/svg%3E")`,
    backgroundRepeat: "repeat",
    backgroundSize: "200px",
    pointerEvents: "none",
    zIndex: 0,
  },
  page: {
    position: "relative",
    zIndex: 1,
    maxWidth: 480,
    margin: "0 auto",
    padding: "24px 20px 60px",
    animation: "fadeIn 0.3s ease",
  },
  header: {
    display: "flex",
    alignItems: "center",
    justifyContent: "space-between",
    marginBottom: 28,
  },
  appTag: {
    fontFamily: "'Space Mono', monospace",
    fontSize: 9,
    letterSpacing: "0.25em",
    color: "#555",
    marginBottom: 2,
  },
  appTitle: {
    fontFamily: "'Syne', sans-serif",
    fontSize: 32,
    fontWeight: 800,
    letterSpacing: "-0.02em",
    color: "#f0ede8",
  },
  iconBtn: {
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    background: "#1a1a1c",
    border: "1px solid #2a2a2c",
    borderRadius: 10,
    width: 38,
    height: 38,
    cursor: "pointer",
    color: "#888",
    fontSize: 16,
    position: "relative",
  },
  trashBadge: {
    position: "absolute",
    top: -5,
    right: -5,
    background: "#f87171",
    color: "#0a0a0b",
    borderRadius: "50%",
    width: 16,
    height: 16,
    fontSize: 9,
    fontWeight: 700,
    fontFamily: "'Space Mono', monospace",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
  },
  langBtn: {
    display: "flex",
    alignItems: "center",
    gap: 6,
    background: "#1a1a1c",
    border: "1px solid #2a2a2c",
    borderRadius: 10,
    padding: "8px 12px",
    cursor: "pointer",
    color: "#e8e6e1",
  },
  langCode: {
    fontFamily: "'Space Mono', monospace",
    fontSize: 11,
    fontWeight: 700,
    letterSpacing: "0.1em",
    color: "#aaa",
  },
  chevron: { color: "#555", fontSize: 12 },
  langDropdown: {
    position: "absolute",
    right: 20,
    top: 80,
    background: "#141416",
    border: "1px solid #252527",
    borderRadius: 12,
    overflow: "hidden",
    zIndex: 100,
    boxShadow: "0 20px 60px rgba(0,0,0,0.6)",
    animation: "fadeIn 0.15s ease",
  },
  langOption: {
    display: "flex",
    alignItems: "center",
    gap: 10,
    width: "100%",
    background: "transparent",
    border: "none",
    padding: "10px 16px",
    cursor: "pointer",
    color: "#ccc",
  },
  langOptionActive: {
    background: "#1f1f22",
    color: "#f0ede8",
  },
  section: { marginBottom: 28 },
  sectionLabel: {
    fontFamily: "'Space Mono', monospace",
    fontSize: 9,
    letterSpacing: "0.2em",
    color: "#444",
    marginBottom: 12,
  },
  sectionRow: {
    display: "flex",
    alignItems: "center",
    justifyContent: "space-between",
    marginBottom: 12,
  },
  newBtn: {
    fontFamily: "'Space Mono', monospace",
    fontSize: 9,
    letterSpacing: "0.15em",
    background: "transparent",
    border: "1px solid #2e7d52",
    color: "#4ade80",
    padding: "5px 10px",
    borderRadius: 6,
    cursor: "pointer",
  },
  memberRow: {
    display: "flex",
    alignItems: "center",
    gap: 16,
    marginBottom: 12,
  },
  counterBtn: {
    width: 40,
    height: 40,
    borderRadius: 10,
    background: "#1a1a1c",
    border: "1px solid #2a2a2c",
    color: "#e8e6e1",
    fontSize: 20,
    cursor: "pointer",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    fontFamily: "'DM Mono', monospace",
  },
  memberDisplay: {
    display: "flex",
    flexDirection: "column",
    alignItems: "center",
    flex: 1,
    background: "#141416",
    border: "1px solid #222",
    borderRadius: 10,
    padding: "10px 0",
  },
  memberNum: {
    fontFamily: "'Syne', sans-serif",
    fontSize: 36,
    fontWeight: 800,
    color: "#f0ede8",
    lineHeight: 1,
  },
  memberSub: {
    fontSize: 10,
    color: "#555",
    fontFamily: "'Space Mono', monospace",
    letterSpacing: "0.1em",
    marginTop: 2,
  },
  memberDots: {
    display: "flex",
    flexWrap: "wrap",
    gap: 6,
    alignItems: "center",
  },
  dot: {
    width: 8,
    height: 8,
    borderRadius: "50%",
    background: "#4ade80",
    opacity: 0.7,
  },
  moreText: {
    fontFamily: "'Space Mono', monospace",
    fontSize: 10,
    color: "#555",
  },
  groupList: {
    display: "flex",
    flexDirection: "column",
    gap: 10,
  },
  groupCard: {
    background: "#111113",
    border: "1px solid #1e1e21",
    borderRadius: 14,
    padding: "14px 16px",
    textAlign: "left",
    cursor: "pointer",
    color: "#e8e6e1",
    position: "relative",
    width: "100%",
  },
  groupTypeBadge: {
    display: "inline-flex",
    alignItems: "center",
    gap: 4,
    fontSize: 10,
    fontFamily: "'Space Mono', monospace",
    letterSpacing: "0.08em",
    border: "1px solid #2e7d5244",
    borderRadius: 6,
    padding: "2px 8px",
    marginBottom: 8,
    background: "#4ade8022",
    color: "#4ade80",
  },
  groupName: {
    fontFamily: "'Syne', sans-serif",
    fontSize: 17,
    fontWeight: 700,
    color: "#f0ede8",
    marginBottom: 6,
    letterSpacing: "-0.01em",
    paddingRight: 32,
  },
  groupMeta: {
    display: "flex",
    gap: 14,
    fontSize: 11,
    color: "#555",
    fontFamily: "'DM Mono', monospace",
  },
  trashCardBtn: {
    position: "absolute",
    right: 14,
    top: "50%",
    transform: "translateY(-50%)",
    background: "transparent",
    border: "none",
    color: "#444",
    cursor: "pointer",
    fontSize: 16,
    padding: 4,
  },
  backBtn: {
    background: "transparent",
    border: "none",
    color: "#888",
    cursor: "pointer",
    fontFamily: "'DM Mono', monospace",
    fontSize: 13,
    padding: 0,
  },
  groupDetailTitle: {
    fontFamily: "'Syne', sans-serif",
    fontSize: 28,
    fontWeight: 800,
    letterSpacing: "-0.02em",
    color: "#f0ede8",
    marginBottom: 24,
    lineHeight: 1.1,
    marginTop: 8,
  },
  memberChips: {
    display: "flex",
    flexWrap: "wrap",
    gap: 8,
  },
  memberChip: {
    display: "flex",
    alignItems: "center",
    gap: 6,
    background: "#141416",
    border: "1px solid #1e1e21",
    borderRadius: 8,
    padding: "5px 10px",
  },
  avatar: {
    width: 22,
    height: 22,
    borderRadius: "50%",
    background: "#1f3a2a",
    border: "1px solid #2e7d52",
    color: "#4ade80",
    fontSize: 10,
    fontWeight: 700,
    fontFamily: "'Space Mono', monospace",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
  },
  memberChipName: {
    fontSize: 11,
    color: "#aaa",
  },
  recordButton: {
    width: "100%",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    gap: 10,
    background: "#0d1f14",
    border: "1px solid #1a3d25",
    borderRadius: 14,
    padding: "16px",
    cursor: "pointer",
    color: "#4ade80",
    transition: "all 0.2s",
  },
  recordButtonActive: {
    background: "#1f0d0d",
    border: "1px solid #3d1a1a",
    color: "#f87171",
  },
  recordButtonProcessing: {
    background: "#0d1420",
    border: "1px solid #1a2d50",
    color: "#60a5fa",
  },
  recordDot: {
    width: 10,
    height: 10,
    borderRadius: "50%",
    background: "currentColor",
  },
  recordLabel: {
    fontFamily: "'Space Mono', monospace",
    fontSize: 11,
    letterSpacing: "0.15em",
    fontWeight: 700,
  },
  recordingIndicator: {
    display: "flex",
    alignItems: "center",
    gap: 8,
    marginTop: 10,
  },
  recordPulse: {
    width: 8,
    height: 8,
    borderRadius: "50%",
    background: "#f87171",
    animation: "pulse 1.2s ease-in-out infinite",
  },
  recordingText: {
    fontSize: 11,
    color: "#888",
    fontFamily: "'DM Mono', monospace",
  },
  processingBar: {
    marginTop: 10,
    background: "#0d1420",
    border: "1px solid #1a2d50",
    borderRadius: 8,
    padding: "10px 14px",
    overflow: "hidden",
    position: "relative",
  },
  processingText: {
    fontSize: 11,
    color: "#60a5fa",
    fontFamily: "'DM Mono', monospace",
  },
  errorBox: {
    marginTop: 10,
    background: "#1f0d0d",
    border: "1px solid #3d1a1a",
    borderRadius: 8,
    padding: "10px 14px",
    fontSize: 11,
    color: "#f87171",
    fontFamily: "'DM Mono', monospace",
  },
  recordingList: {
    display: "flex",
    flexDirection: "column",
    gap: 8,
  },
  recordingCard: {
    display: "flex",
    alignItems: "flex-start",
    gap: 12,
    background: "#111113",
    border: "1px solid #1e1e21",
    borderRadius: 12,
    padding: "12px 14px",
  },
  recIconWrap: {
    width: 36,
    height: 36,
    borderRadius: 10,
    background: "#1a1a1c",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    flexShrink: 0,
    marginTop: 2,
  },
  recInfo: {
    flex: 1,
    minWidth: 0,
  },
  recTitle: {
    fontFamily: "'Syne', sans-serif",
    fontSize: 13,
    fontWeight: 700,
    color: "#e8e6e1",
    marginBottom: 3,
    whiteSpace: "nowrap",
    overflow: "hidden",
    textOverflow: "ellipsis",
  },
  recMeta: {
    display: "flex",
    gap: 10,
    fontSize: 10,
    color: "#444",
    fontFamily: "'DM Mono', monospace",
    flexWrap: "wrap",
  },
  recTranscription: {
    marginTop: 6,
    fontSize: 10,
    color: "#666",
    fontFamily: "'DM Mono', monospace",
    lineHeight: 1.5,
    borderLeft: "2px solid #2a2a2c",
    paddingLeft: 8,
  },
  pdfLink: {
    display: "inline-block",
    marginTop: 6,
    fontSize: 10,
    color: "#60a5fa",
    fontFamily: "'DM Mono', monospace",
    textDecoration: "none",
  },
  playBtn: {
    width: 34,
    height: 34,
    borderRadius: "50%",
    background: "#1a1a1c",
    border: "1px solid #2a2a2c",
    color: "#888",
    cursor: "pointer",
    fontSize: 12,
    flexShrink: 0,
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    marginTop: 2,
  },
  playBtnActive: {
    background: "#0d1f14",
    border: "1px solid #2e7d52",
    color: "#4ade80",
  },
  playBtnDisabled: {
    opacity: 0.3,
    cursor: "not-allowed",
  },
  emptyState: {
    textAlign: "center",
    color: "#333",
    fontFamily: "'Space Mono', monospace",
    fontSize: 11,
    letterSpacing: "0.1em",
    padding: "24px 0",
    border: "1px dashed #1e1e21",
    borderRadius: 12,
    whiteSpace: "pre-line",
    lineHeight: 1.8,
  },
  // Trash screen
  emptyTrashBtn: {
    width: "100%",
    background: "transparent",
    border: "1px solid #3d1a1a",
    borderRadius: 10,
    color: "#f87171",
    padding: "10px",
    cursor: "pointer",
    fontFamily: "'Space Mono', monospace",
    fontSize: 10,
    letterSpacing: "0.1em",
    marginBottom: 16,
  },
  trashActions: {
    display: "flex",
    gap: 8,
    marginTop: 10,
  },
  restoreBtn: {
    flex: 1,
    background: "#0d1f14",
    border: "1px solid #2e7d52",
    borderRadius: 8,
    color: "#4ade80",
    padding: "8px 10px",
    cursor: "pointer",
    fontFamily: "'DM Mono', monospace",
    fontSize: 11,
  },
  deleteBtn: {
    flex: 1,
    background: "#1f0d0d",
    border: "1px solid #3d1a1a",
    borderRadius: 8,
    color: "#f87171",
    padding: "8px 10px",
    cursor: "pointer",
    fontFamily: "'DM Mono', monospace",
    fontSize: 11,
  },
  // Modals
  modalOverlay: {
    position: "fixed",
    inset: 0,
    background: "rgba(0,0,0,0.8)",
    backdropFilter: "blur(8px)",
    zIndex: 200,
    display: "flex",
    alignItems: "flex-end",
    justifyContent: "center",
  },
  modal: {
    background: "#111113",
    border: "1px solid #1e1e21",
    borderRadius: "20px 20px 0 0",
    padding: "24px 20px 40px",
    width: "100%",
    maxWidth: 480,
    animation: "fadeIn 0.2s ease",
    maxHeight: "90vh",
    overflowY: "auto",
  },
  modalTitle: {
    fontFamily: "'Space Mono', monospace",
    fontSize: 10,
    letterSpacing: "0.25em",
    color: "#444",
    marginBottom: 20,
  },
  formGroup: { marginBottom: 18 },
  formLabel: {
    fontFamily: "'Space Mono', monospace",
    fontSize: 9,
    letterSpacing: "0.2em",
    color: "#444",
    display: "block",
    marginBottom: 8,
  },
  formInput: {
    width: "100%",
    background: "#0e0e10",
    border: "1px solid #222",
    borderRadius: 10,
    padding: "10px 12px",
    color: "#e8e6e1",
    fontFamily: "'DM Mono', monospace",
    fontSize: 13,
    outline: "none",
    marginBottom: 6,
  },
  memberInputRow: {
    display: "flex",
    gap: 8,
    alignItems: "center",
    marginBottom: 6,
  },
  removeMemberBtn: {
    background: "transparent",
    border: "none",
    color: "#444",
    cursor: "pointer",
    fontSize: 12,
    flexShrink: 0,
  },
  addMemberBtn: {
    background: "transparent",
    border: "1px dashed #222",
    borderRadius: 10,
    color: "#444",
    padding: "8px 12px",
    cursor: "pointer",
    fontFamily: "'DM Mono', monospace",
    fontSize: 11,
    width: "100%",
    marginTop: 4,
  },
  modalActions: {
    display: "flex",
    gap: 10,
    marginTop: 4,
  },
  cancelBtn: {
    flex: 1,
    background: "transparent",
    border: "1px solid #222",
    borderRadius: 10,
    color: "#555",
    padding: "12px",
    cursor: "pointer",
    fontFamily: "'DM Mono', monospace",
    fontSize: 12,
  },
  createBtn: {
    flex: 2,
    background: "#0d1f14",
    border: "1px solid #2e7d52",
    borderRadius: 10,
    color: "#4ade80",
    padding: "12px",
    cursor: "pointer",
    fontFamily: "'Space Mono', monospace",
    fontSize: 11,
    letterSpacing: "0.1em",
    fontWeight: 700,
  },
};
