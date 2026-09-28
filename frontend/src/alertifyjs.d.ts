declare module 'alertifyjs' {
  interface AlertifyConfirmDialog {
    set(key: 'labels', value: { ok: string; cancel: string }): AlertifyConfirmDialog;
    set(key: 'defaultFocus', value: 'ok' | 'cancel'): AlertifyConfirmDialog;
    close(): void;
  }

  interface Alertify {
    success(message: string): void;
    error(message: string): void;
    dismissAll(): void;
    confirm(
      title: string,
      message: string,
      onOk: () => void,
      onCancel: () => void,
    ): AlertifyConfirmDialog;
  }

  const alertify: Alertify;
  export default alertify;
}
