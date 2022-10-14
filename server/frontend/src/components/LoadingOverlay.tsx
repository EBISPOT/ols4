export interface Props {
  message?: string;
}

export default function LoadingOverlay({ message }: Props) {
  return (
    <div className="fixed top-0 left-0 w-full h-full z-50 backdrop-blur-sm">
      <div className="absolute top-1/2 left-1/2 ">
        <div className="overlay-spinner"></div>
        <div className="font-semibold">{message}</div>
      </div>
    </div>
  );
}
