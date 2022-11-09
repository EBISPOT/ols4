export function asArray<T>(obj: T | T[]): T[] {
  if (Array.isArray(obj)) {
    return obj;
  } else if (obj) {
    return [obj];
  }
  return []
}

export function randomString() {
  return (Math.random() * Math.pow(2, 54)).toString(36);
}
