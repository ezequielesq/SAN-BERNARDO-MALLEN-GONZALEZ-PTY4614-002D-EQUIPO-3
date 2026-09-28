declare module 'alertifyjs' {
  interface AlertifyConfirmDialog {
    set(key: 'labels', value: { ok: string; cancel: string }): AlertifyConfirmDialog;
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
