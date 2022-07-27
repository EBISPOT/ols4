
import { formatRelative, parseISO } from 'date-fns'
import locale from "date-fns/locale/en-GB"

export default function formatDate(dateStr:string) {
    return formatRelative(parseISO(dateStr), new Date(), { locale })
}

