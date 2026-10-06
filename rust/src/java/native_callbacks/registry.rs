use crate::proto::patchbukkit::registry::{
    GetRegistryDataRequest, GetRegistryDataResponse, ItemDefinition, ItemRegistryData,
    RegistryType, SoundEvent, SoundEventRegistryData, get_registry_data_response::Registry,
};

pub fn ffi_native_bridge_get_registry_data_impl(
    request: GetRegistryDataRequest,
) -> Option<GetRegistryDataResponse> {
    let registry = match request.registry {
        val if val == RegistryType::SoundEvent as i32 => {
            let sounds = pumpkin_data::sound::Sound::slice()
                .iter()
                .map(|s| SoundEvent {
                    id: *s as u32,
                    name: s.to_name().to_string(),
                })
                .collect::<Vec<_>>();
            Registry::SoundEvent(SoundEventRegistryData {
                sound_events: sounds,
            })
        }
        val if val == RegistryType::Item as i32 => {
            use pumpkin_data::{data_component::DataComponent, item::Item, item_stack::ItemStack};
            let items = (0..=u16::MAX)
                .filter_map(Item::from_id)
                .map(|item| {
                    let stack = ItemStack::static_new_java(1, item);
                    ItemDefinition {
                        name: format!("minecraft:{}", item.registry_key),
                        max_stack_size: u32::from(stack.get_max_stack_size()),
                        max_durability: stack.get_max_damage().unwrap_or(0).max(0) as u32,
                        edible: stack.has_data_component(DataComponent::Food)
                            && stack.has_data_component(DataComponent::Consumable),
                        record: stack.has_data_component(DataComponent::JukeboxPlayable),
                    }
                })
                .collect();
            Registry::Item(ItemRegistryData { items })
        }
        _ => {
            tracing::warn!(
                registry = request.registry,
                "Unsupported Bukkit registry request"
            );
            return None;
        }
    };

    Some(GetRegistryDataResponse {
        registry: Some(registry),
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn item_metadata_uses_generated_default_components() {
        let response = ffi_native_bridge_get_registry_data_impl(GetRegistryDataRequest {
            registry: RegistryType::Item as i32,
        })
        .expect("item registry must be supported");
        let Some(Registry::Item(registry)) = response.registry else {
            panic!("expected item registry");
        };
        let find = |name: &str| {
            registry
                .items
                .iter()
                .find(|item| item.name == name)
                .expect("vanilla item must exist")
        };
        assert_eq!(find("minecraft:stone").max_stack_size, 64);
        assert_eq!(find("minecraft:ender_pearl").max_stack_size, 16);
        let sword = find("minecraft:diamond_sword");
        assert_eq!((sword.max_stack_size, sword.max_durability), (1, 1561));
        assert!(find("minecraft:apple").edible);
        assert!(!find("minecraft:stone").edible);
        assert!(find("minecraft:music_disc_13").record);
        assert!(!find("minecraft:stone").record);
        assert!(
            !registry
                .items
                .iter()
                .any(|item| item.name == "minecraft:water")
        );
    }

    #[test]
    fn unsupported_registry_request_is_refused_without_panic() {
        assert!(
            ffi_native_bridge_get_registry_data_impl(GetRegistryDataRequest { registry: -1 })
                .is_none()
        );
    }
}
