import { useEffect, useRef } from "react";
import Class from "../model/Class";
import Entity from "../model/Entity";
import Individual from "../model/Individual";
import Property from "../model/Property";

export function asArray<T>(obj: T | T[]): T[] {
  if (Array.isArray(obj)) {
    return obj;
  } else if (obj) {
    return [obj];
  }
  return [];
}

export function randomString() {
  return (Math.random() * Math.pow(2, 54)).toString(36);
}

export function sortByKeys(a: any, b: any) {
  const keyA = a.key.toUpperCase();
  const keyB = b.key.toUpperCase();
  return keyA === keyB ? 0 : keyA > keyB ? 1 : -1;
}

export async function copyToClipboard(text: string) {
  if ("clipboard" in navigator) {
    return await navigator.clipboard.writeText(text);
  } else {
    return document.execCommand("copy", true, text);
  }
}

export function usePrevious(value: any) {
  const ref = useRef();
  useEffect(() => {
    ref.current = value;
  }, [value]);
  return ref.current;
}
export function thingFromJsonProperties(properties: any): Entity {
  const types = properties["type"] || [];
  if (types.indexOf("class") !== -1) return new Class(properties);
  if (types.indexOf("property") !== -1) return new Property(properties);
  if (types.indexOf("individual") !== -1) return new Individual(properties);
  throw new Error("Unknown entity type: " + JSON.stringify(properties));
}

export function toCamel(str: string) {
  return str.replace(/([-_][a-z])/gi, ($1) => {
    return $1.toUpperCase().replace("-", "").replace("_", "");
  });
}
